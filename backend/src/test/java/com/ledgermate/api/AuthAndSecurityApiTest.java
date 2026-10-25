package com.ledgermate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgermate.config.AppProperties;
import com.ledgermate.entity.User;
import com.ledgermate.repository.UserRepository;
import com.ledgermate.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthAndSecurityApiTest extends AbstractApiTest {

    @Autowired UserRepository users;
    @Value("${app.security.jwt-secret}") String jwtSecret;

    // ------------------------------------------------------------ registration and login

    @Test
    void registerLoginAndMeWork() throws Exception {
        String email = "Mixed.Case-" + UUID.randomUUID() + "@Example.com";
        Session s = register(email);

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn();
        String setCookie = String.join(";", login.getResponse().getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains("LM_AUTH="), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);

        getAs(s, "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    }

    @Test
    void passwordsAreStoredAsBCryptHashesNeverPlaintext() throws Exception {
        Session s = register();
        User stored = users.findByEmail(s.email()).orElseThrow();
        assertTrue(stored.getPasswordHash().startsWith("$2"), "expected a BCrypt hash");
        assertFalse(stored.getPasswordHash().contains(PASSWORD));
    }

    @Test
    void badCredentialsAreRejectedWithTheSameMessageForUnknownAndKnownEmails() throws Exception {
        Session s = register();
        String known = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", s.email(), "password", "Wrong-password-1"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "password", "Wrong-password-1"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(known).get("message").asText(), json.readTree(unknown).get("message").asText());
    }

    @Test
    void registrationValidatesInputAndRejectsDuplicates() throws Exception {
        Session s = register();
        postAnonymous("/api/auth/register", Map.of("email", s.email(), "password", PASSWORD))
                .andExpect(status().isConflict());
        postAnonymous("/api/auth/register", Map.of("email", "short-" + UUID.randomUUID() + "@example.com", "password", "short"))
                .andExpect(status().isBadRequest());
        postAnonymous("/api/auth/register", Map.of("email", "not-an-email", "password", PASSWORD))
                .andExpect(status().isBadRequest());
        postAnonymous("/api/auth/register", Map.of("password", PASSWORD)).andExpect(status().isBadRequest());
    }

    @Test
    void profileTimezoneCanBeChangedToAValidZoneOnly() throws Exception {
        Session s = register();
        mvc.perform(as(patch("/api/auth/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\",\"timezone\":\"Europe/London\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.displayName").value("New Name"));
        mvc.perform(as(patch("/api/auth/me"), s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ authentication required

    @Test
    void protectedEndpointsRejectAnonymousRequests() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/people")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/people/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/people/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/people/" + id + "/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/people/" + id + "/reports/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/people").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedExpiredAndForeignSignedTokensAreRejected() throws Exception {
        Session s = register();
        mvc.perform(get("/api/people").header("Authorization", "Bearer " + s.token() + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/people").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());

        SecretKey realKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        String expired = Jwts.builder().issuer("ledgermate").subject(s.userId().toString()).claim("email", s.email())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(realKey).compact();
        mvc.perform(get("/api/people").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());

        byte[] otherBytes = new byte[48];
        Arrays.fill(otherBytes, (byte) 7);
        String foreign = Jwts.builder().issuer("ledgermate").subject(s.userId().toString())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(otherBytes)).compact();
        mvc.perform(get("/api/people").header("Authorization", "Bearer " + foreign)).andExpect(status().isUnauthorized());

        String wrongIssuer = Jwts.builder().issuer("someone-else").subject(s.userId().toString())
                .expiration(Date.from(Instant.now().plusSeconds(3600))).signWith(realKey).compact();
        mvc.perform(get("/api/people").header("Authorization", "Bearer " + wrongIssuer)).andExpect(status().isUnauthorized());

        String unknownUser = Jwts.builder().issuer("ledgermate").subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plusSeconds(3600))).signWith(realKey).compact();
        mvc.perform(get("/api/people").header("Authorization", "Bearer " + unknownUser)).andExpect(status().isUnauthorized());
    }

    @Test
    void aWeakOrMalformedJwtSecretStopsTheApplicationFromStarting() {
        AppProperties.Security weak = new AppProperties.Security("c2hvcnQ=", "ledgermate", 5, "LM_AUTH", false, false);
        assertThrows(IllegalStateException.class, () -> new JwtService(new AppProperties(weak, null, null)));
        AppProperties.Security notBase64 = new AppProperties.Security("***not base64***", "ledgermate", 5, "LM_AUTH", false, false);
        assertThrows(IllegalStateException.class, () -> new JwtService(new AppProperties(notBase64, null, null)));
    }

    // ------------------------------------------------------------ CSRF and CORS

    @Test
    void cookieAuthenticatedWritesNeedTheCsrfToken() throws Exception {
        Session s = register();
        Cookie auth = new Cookie("LM_AUTH", s.token());
        String payload = json.writeValueAsString(personBody("Via cookie", null));

        MvcResult me = mvc.perform(get("/api/auth/me").cookie(auth)).andExpect(status().isOk()).andReturn();
        String xsrf = cookieValue(me, "XSRF-TOKEN");
        assertNotNull(xsrf, "the server must hand out an XSRF-TOKEN cookie");

        mvc.perform(post("/api/people").cookie(auth).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/people").cookie(auth, new Cookie("XSRF-TOKEN", "forged-value"))
                        .header("X-XSRF-TOKEN", "different-value")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/people").cookie(auth, new Cookie("XSRF-TOKEN", xsrf))
                        .header("X-XSRF-TOKEN", xsrf)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void logoutClearsTheAuthCookie() throws Exception {
        Session s = register();
        MvcResult logout = mvc.perform(as(post("/api/auth/logout"), s)).andExpect(status().isNoContent()).andReturn();
        String setCookie = String.join(";", logout.getResponse().getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains("LM_AUTH=;") || setCookie.contains("LM_AUTH=\"\""), setCookie);
        assertTrue(setCookie.contains("Max-Age=0"), setCookie);
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/people").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/people").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ ownership isolation

    @Test
    void aUserCanNeverReadModifyReverseExportOrArchiveAnotherUsersRecords() throws Exception {
        Session owner = register();
        Session intruder = register();
        String personId = createSpecExampleLedger(owner, "Private ledger");
        JsonNode firstTx = body(getAs(owner, "/api/people/" + personId + "/transactions?sortBy=date&direction=asc")
                .andExpect(status().isOk()).andReturn()).get("items").get(0);
        String txId = firstTx.get("id").asText();
        String p = "/api/people/" + personId;

        getAs(intruder, p).andExpect(status().isNotFound());
        getAs(intruder, p + "/summary").andExpect(status().isNotFound());
        getAs(intruder, p + "/transactions").andExpect(status().isNotFound());
        getAs(intruder, "/api/transactions/" + txId).andExpect(status().isNotFound());
        getAs(intruder, p + "/reports/summary").andExpect(status().isNotFound());
        getAs(intruder, p + "/reports/transactions").andExpect(status().isNotFound());
        getAs(intruder, p + "/reports/export").andExpect(status().isNotFound());

        postAs(intruder, p + "/transactions", txBody("CREDIT", "1", "2025-10-24", "10:00", "Mallory", null))
                .andExpect(status().isNotFound());
        mvc.perform(as(put(p), intruder).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(personBody("Hijacked", null))))
                .andExpect(status().isNotFound());
        mvc.perform(as(patch(p + "/archive"), intruder)).andExpect(status().isNotFound());
        mvc.perform(as(patch(p + "/restore"), intruder)).andExpect(status().isNotFound());
        mvc.perform(as(patch("/api/transactions/" + txId + "/description"), intruder)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"defaced\"}"))
                .andExpect(status().isNotFound());
        postAs(intruder, "/api/transactions/" + txId + "/reverse", Map.of("reason", "attack")).andExpect(status().isNotFound());
        Map<String, Object> correction = new LinkedHashMap<>();
        correction.put("reason", "attack");
        correction.put("replacement", txBody("CREDIT", "1", "2025-10-24", "10:00", "Mallory", null));
        postAs(intruder, "/api/transactions/" + txId + "/corrections", correction).andExpect(status().isNotFound());

        getAs(intruder, "/api/people").andExpect(jsonPath("$.length()").value(0));
        getAs(intruder, "/api/people/overview").andExpect(jsonPath("$.activeLedgers").value(0));

        // the owner's ledger is exactly as it was
        getAs(owner, p).andExpect(jsonPath("$.name").value("Private ledger")).andExpect(jsonPath("$.archived").value(false));
        getAs(owner, p + "/summary")
                .andExpect(jsonPath("$.transactionCount").value(4))
                .andExpect(jsonPath("$.currentBalance").value("5000.00"));
        getAs(owner, "/api/transactions/" + txId)
                .andExpect(jsonPath("$.description").value("Money received from Ravi"))
                .andExpect(jsonPath("$.reversedByTransactionId").doesNotExist());
    }

    // ------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions postAnonymous(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static String cookieValue(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                return header.substring(name.length() + 1, end < 0 ? header.length() : end);
            }
        }
        return null;
    }
}
