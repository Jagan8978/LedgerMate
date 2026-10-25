package com.ledgermate.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared helpers for the integration tests. They run the real application (security, validation,
 * services, Flyway migrations, JPA) against an in-memory H2 database in PostgreSQL mode.
 * Every test registers its own users, so tests never depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractApiTest {

    static final String PASSWORD = "CorrectHorse-9-Battery";

    record Session(String token, UUID userId, String email) {}

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected Session register() throws Exception {
        return register("user-" + UUID.randomUUID() + "@example.com");
    }

    protected Session register(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Tester"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = body(r);
        return new Session(body.get("accessToken").asText(), UUID.fromString(body.get("user").get("id").asText()), email);
    }

    protected static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header("Authorization", "Bearer " + session.token());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected ResultActions getAs(Session session, String url) throws Exception {
        return mvc.perform(as(get(url), session));
    }

    protected ResultActions postAs(Session session, String url, Object body) throws Exception {
        return mvc.perform(as(post(url), session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected Map<String, Object> personBody(String name, String openingBalance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        if (openingBalance != null) {
            m.put("openingBalance", openingBalance);
        }
        return m;
    }

    protected String createPerson(Session session, String name, String openingBalance) throws Exception {
        MvcResult r = postAs(session, "/api/people", personBody(name, openingBalance))
                .andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asText();
    }

    protected Map<String, Object> txBody(String type, String amount, String date, String time,
                                         String counterparty, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("amount", amount);
        m.put("transactionDate", date);
        if (time != null) {
            m.put("transactionTime", time);
        }
        m.put("counterpartyName", counterparty);
        if (description != null) {
            m.put("description", description);
        }
        return m;
    }

    protected JsonNode addTx(Session session, String personId, String type, String amount, String date,
                             String time, String counterparty, String description) throws Exception {
        MvcResult r = postAs(session, "/api/people/" + personId + "/transactions",
                txBody(type, amount, date, time, counterparty, description))
                .andExpect(status().isCreated()).andReturn();
        return body(r);
    }

    /** Posts the four entries of the worked example from the specification (balance 5,000.00). */
    protected String createSpecExampleLedger(Session session, String name) throws Exception {
        String id = createPerson(session, name, null);
        addTx(session, id, "CREDIT", "5000", "2025-10-22", "10:00", "Ravi", "Money received from Ravi");
        addTx(session, id, "DEBIT", "1500", "2025-10-22", "11:00", "Suresh", "Money paid to Suresh");
        addTx(session, id, "CREDIT", "2000", "2025-10-23", "10:00", "Kumar", "Repayment received from Kumar");
        addTx(session, id, "DEBIT", "500", "2025-10-23", "15:00", "Ravi", "Money paid to Ravi");
        return id;
    }
}
