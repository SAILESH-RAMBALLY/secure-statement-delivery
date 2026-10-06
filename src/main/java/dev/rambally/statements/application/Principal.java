package dev.rambally.statements.application;

import dev.rambally.statements.domain.CustomerId;

/**
 * Who is calling. Constructed only by the web adapter from the verified JWT (subject and roles);
 * no use case ever accepts a caller identity from a URL or request body.
 */
public record Principal(CustomerId customerId, boolean admin) {

    public Principal {
        if (customerId == null) {
            throw new IllegalArgumentException("principal must have a customer id");
        }
    }

    public boolean mayAccess(CustomerId owner) {
        return admin || customerId.equals(owner);
    }
}
