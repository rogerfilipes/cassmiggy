package cassmiggy.examples.quarkus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Reads the {@code app.users} table that the startup migrations create and seed.
 *
 * <p>This is what makes the example a real, interactive Quarkus application: once it boots,
 * {@code cassmiggy-quarkus} has already applied the migrations on {@link io.quarkus.runtime.StartupEvent},
 * so hitting this endpoint proves they ran:
 *
 * <pre>{@code
 * curl localhost:8080/users
 * }</pre>
 */
@Path("/users")
public class UsersResource {

    @Inject
    QuarkusCqlSession session;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> list() {
        List<User> users = new ArrayList<>();
        for (Row row : session.execute("SELECT id, email, created_at FROM app.users")) {
            users.add(new User(row.getUuid("id"), row.getString("email"), row.getInstant("created_at")));
        }
        return users;
    }

    public record User(UUID id, String email, Instant createdAt) {}
}
