package com.ledgermate.config;

import com.ledgermate.dto.PersonDtos.PersonRequest;
import com.ledgermate.dto.PersonDtos.PersonResponse;
import com.ledgermate.dto.TransactionDtos.TransactionRequest;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.entity.User;
import com.ledgermate.repository.UserRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.PersonService;
import com.ledgermate.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optional demo data (off by default). Enable with DEMO_DATA_ENABLED=true and DEMO_PASSWORD=...
 * Creates demo@ledgermate.local with the worked example from the specification:
 * credits 5,000 + 2,000, debits 1,500 + 500 -> balance 5,000.00.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    static final String DEMO_EMAIL = "demo@ledgermate.local";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PersonService persons;
    private final TransactionService transactions;
    private final AppProperties props;

    public DemoDataLoader(UserRepository users, PasswordEncoder encoder, PersonService persons,
                          TransactionService transactions, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.persons = persons;
        this.transactions = transactions;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = props.demo().password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DEMO_PASSWORD must be set (10+ characters) when DEMO_DATA_ENABLED=true.");
        }
        if (users.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        User user = users.save(new User(DEMO_EMAIL, "Demo User", encoder.encode(password)));
        AuthenticatedUser actor = new AuthenticatedUser(user.getId(), user.getEmail());
        LocalDate base = LocalDate.now(ZoneId.of(user.getTimezone())).minusDays(3);

        PersonResponse a = persons.create(actor, new PersonRequest("Person A (demo)", "98765 43210",
                "Worked example from the specification", BigDecimal.ZERO, "Demo data"));
        add(actor, a, TransactionType.CREDIT, "5000", base, 10, "Ravi", "Money received from Ravi");
        add(actor, a, TransactionType.DEBIT, "1500", base, 11, "Suresh", "Money paid to Suresh");
        add(actor, a, TransactionType.CREDIT, "2000", base.plusDays(1), 10, "Kumar", "Repayment received from Kumar");
        add(actor, a, TransactionType.DEBIT, "500", base.plusDays(1), 15, "Ravi", "Money paid to Ravi");

        PersonResponse b = persons.create(actor, new PersonRequest("Person B (demo)", null,
                "Ledger with an opening balance", new BigDecimal("1000.00"), null));
        add(actor, b, TransactionType.DEBIT, "250.50", base.plusDays(2), 9, "Meera", "Groceries");
    }

    private void add(AuthenticatedUser actor, PersonResponse person, TransactionType type, String amount,
                     LocalDate date, int hour, String counterparty, String description) {
        transactions.create(actor, person.id(),
                new TransactionRequest(type, new BigDecimal(amount), date, LocalTime.of(hour, 0),
                        counterparty, description, null, null), null);
    }
}
