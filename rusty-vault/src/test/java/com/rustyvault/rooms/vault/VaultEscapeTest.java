package com.rustyvault.rooms.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rustyvault.CustomerDirectory;
import com.rustyvault.RustyVault;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 3 - THE INJECTION VAULT - the lock on the door.
 *
 * <p><b>LOCKED FILE.</b> Every test here fails against the legacy code, which builds its
 * SQL by gluing text together. The room is escaped when all of them pass and the golden
 * master is still green.
 */
@Tag("escape")
@DisplayName("Injection Vault: the queries that bite")
class VaultEscapeTest {

    /** Five customers, ids 1 to 5. */
    private static CustomerDirectory seeded() {
        CustomerDirectory directory = RustyVault.openDirectory();
        directory.addCustomer("Anna", "Gdansk", 150000);
        directory.addCustomer("Bob", "Krakow", 20000);
        directory.addCustomer("Carl", "Gdansk", 90000);
        directory.addCustomer("Dora", "Warsaw", 20000);
        directory.addCustomer("O'Hara", "Cork", 5000);
        return directory;
    }

    private static List<String> ids(List<String> rows) {
        List<String> ids = new ArrayList<>();
        for (String row : rows) {
            ids.add(row.substring(0, row.indexOf('|')));
        }
        return ids;
    }

    @Test
    @DisplayName("the sort key is one of four words and nothing else")
    void sortKeyIsAllowListed() {
        CustomerDirectory directory = seeded();
        String[] bad = {"name, id DESC", "name; DELETE FROM customer", "(SELECT 1)", "balance_cents",
                "NAME", "Balance", "nope", "", " name", null};
        for (String key : bad) {
            assertThrows(IllegalArgumentException.class, () -> directory.list(key, false),
                    String.valueOf(key));
        }
        assertEquals(5, directory.count());
    }

    @Test
    @DisplayName("a customer id is digits only")
    void idsAreDigitsOnly() {
        CustomerDirectory directory = seeded();
        String[] bad = {"1 OR 1=1", "1; DELETE FROM customer", "abc", "", " 1", "1 ", "-1", "+1",
                "1.0", "0x1", "1234567890123456789", null};
        for (String id : bad) {
            assertThrows(IllegalArgumentException.class, () -> directory.getById(id),
                    "getById " + id);
            assertThrows(IllegalArgumentException.class, () -> directory.sameCityAs(id),
                    "sameCityAs " + id);
        }
        assertEquals("1|Anna|Gdansk|150000", directory.getById("1"));
        assertEquals("1|Anna|Gdansk|150000", directory.getById("0001"));
        assertEquals(5, directory.count());
    }

    @Test
    @DisplayName("every id in an id list is checked, and an empty list is fine")
    void idListsAreChecked() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of(), directory.findByIds(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> directory.findByIds(List.of("1", "2) OR (1=1")));
        assertThrows(IllegalArgumentException.class, () -> directory.findByIds(List.of("1", "x")));
        assertThrows(IllegalArgumentException.class, () -> directory.findByIds(List.of("1", "")));
        assertThrows(IllegalArgumentException.class, () -> directory.findByIds(null));
        List<String> withNull = new ArrayList<>();
        withNull.add("1");
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> directory.findByIds(withNull));
        assertEquals(List.of("2", "4"), ids(directory.findByIds(List.of("4", "2"))));
    }

    @Test
    @DisplayName("an amount is digits only")
    void amountsAreDigitsOnly() {
        CustomerDirectory directory = seeded();
        String[] bad = {"0 OR 1=1", "-1", "+5", "1.5", "1e3", " 5", "", "ten", null};
        for (String amount : bad) {
            assertThrows(IllegalArgumentException.class, () -> directory.richerThan(amount),
                    String.valueOf(amount));
        }
        assertEquals(List.of("1", "3"), ids(directory.richerThan("20000")));
    }

    @Test
    @DisplayName("percent signs and underscores in a prefix are just characters")
    void wildcardsAreLiteral() {
        CustomerDirectory directory = seeded();
        directory.addCustomer("100% Real", "Gdansk", 1);
        directory.addCustomer("a_b", "Gdansk", 1);
        directory.addCustomer("axb", "Gdansk", 1);
        assertEquals(List.of(), directory.searchByNamePrefix("%"));
        assertEquals(List.of("6"), ids(directory.searchByNamePrefix("100%")));
        assertEquals(List.of("7"), ids(directory.searchByNamePrefix("a_")));
        assertEquals(List.of("7"), ids(directory.searchByNamePrefix("A_B")));
        assertEquals(List.of(), directory.searchByNamePrefix("_"));
        assertEquals(List.of(), directory.searchByNamePrefix("\\"));
    }

    @Test
    @DisplayName("a prefix with an apostrophe works and cannot rewrite the query")
    void prefixCannotBeInjected() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("5"), ids(directory.searchByNamePrefix("O'H")));
        assertEquals(List.of(), directory.searchByNamePrefix("x%' OR '1'='1"));
        assertEquals(List.of(), directory.searchByNamePrefix("x' OR UPPER(name) LIKE UPPER('"));
        assertEquals(List.of(), directory.searchByNamePrefix("x'); DELETE FROM customer; --"));
        assertEquals(5, directory.count());
    }

    @Test
    @DisplayName("a city stored today cannot attack the query that reads it tomorrow")
    void storedValuesAreNotTrusted() {
        CustomerDirectory directory = seeded();
        long trap = directory.addCustomer("Mallory", "x' OR '1'='1", 1);
        assertEquals(List.of(), directory.sameCityAs(String.valueOf(trap)));
        long azur1 = directory.addCustomer("Louis", "Cote d'Azur", 1);
        long azur2 = directory.addCustomer("Marie", "Cote d'Azur", 1);
        assertEquals(List.of(String.valueOf(azur2)), ids(directory.sameCityAs(String.valueOf(azur1))));
        assertEquals(List.of(String.valueOf(azur1)), ids(directory.sameCityAs(String.valueOf(azur2))));
        assertEquals(8, directory.count());
    }

    @Test
    @DisplayName("deleting a city cannot delete the others")
    void deleteCannotWipeTheTable() {
        CustomerDirectory directory = seeded();
        assertEquals(0, directory.deleteByCity("nowhere' OR '1'='1"));
        assertEquals(0, directory.deleteByCity("x'; DELETE FROM customer; --"));
        assertEquals(5, directory.count());
    }

    @Test
    @DisplayName("a city with an apostrophe can be deleted, and only that city")
    void deleteWorksWithApostrophes() {
        CustomerDirectory directory = seeded();
        directory.addCustomer("Louis", "Cote d'Azur", 1);
        directory.addCustomer("Marie", "Cote d'Azur", 1);
        assertEquals(2, directory.deleteByCity("Cote d'Azur"));
        assertEquals(5, directory.count());
        assertNull(directory.getById("6"));
    }
}
