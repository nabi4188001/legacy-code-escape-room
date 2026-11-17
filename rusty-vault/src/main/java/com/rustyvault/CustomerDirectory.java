package com.rustyvault;

import java.util.List;

/**
 * The customer directory of Rusty Bank, backed by a SQL database.
 *
 * <p><b>LOCKED FILE.</b> This interface is the contract the room is tested through. An
 * escaping agent may rewrite anything behind it, but may not change this file.
 *
 * <h2>Rows</h2>
 * A customer is returned as one text row, {@code id|name|city|balanceCents}, for example
 * {@code 7|Anna|Gdansk|150000}. Rows are returned ordered by id unless a method says
 * otherwise.
 *
 * <h2>Data is data</h2>
 * Names and cities may contain any characters at all: quotes, percent signs, underscores,
 * backslashes, semicolons, SQL keywords. Whatever the caller sends is stored and matched
 * exactly as written and can never change what a query does.
 *
 * <h2>Numbers that arrive as text</h2>
 * Customer ids and amounts reach the directory as text from web forms. Such a text must be
 * one to eighteen ASCII digits and nothing else: no spaces, no sign, no decimal point. A
 * null or any other text throws {@link IllegalArgumentException}.
 *
 * <h2>Errors</h2>
 * Bad arguments throw {@link IllegalArgumentException} before anything is run against the
 * database. A database failure throws {@link IllegalStateException}.
 */
public interface CustomerDirectory {

    /**
     * Adds a customer and returns the new id (1, 2, 3, ... in order of insertion). The
     * name and city must not be null or blank, the balance must not be negative.
     */
    long addCustomer(String name, String city, long balanceCents);

    /** Customers whose name is exactly the given name. Case matters. */
    List<String> findByName(String name);

    /**
     * Customers whose name starts with the prefix, ignoring case. An empty prefix matches
     * everybody. {@code %} and {@code _} in the prefix are ordinary characters.
     */
    List<String> searchByNamePrefix(String prefix);

    /**
     * Everybody, sorted by one of exactly these keys: {@code id}, {@code name},
     * {@code city}, {@code balance}. Any other key (including a different case, a null, or
     * a key with anything extra attached) is refused. Customers that tie are ordered by id,
     * lowest first, whichever direction was asked for.
     */
    List<String> list(String sortBy, boolean descending);

    /** The customer with this id, or null if there is none. */
    String getById(String id);

    /**
     * The customers with these ids, ordered by id, each at most once. Ids nobody has are
     * ignored; an empty list gives an empty result. Every id in the list is checked.
     */
    List<String> findByIds(List<String> ids);

    /** Customers whose balance is strictly more than the amount, in cents. */
    List<String> richerThan(String minBalanceCents);

    /**
     * The other customers who live in the same city as the given customer, not including
     * that customer. An unknown id is refused with {@link IllegalArgumentException}.
     */
    List<String> sameCityAs(String id);

    /** Deletes everybody who lives in exactly this city and returns how many were deleted. */
    int deleteByCity(String city);

    /** How many customers there are. */
    int count();
}
