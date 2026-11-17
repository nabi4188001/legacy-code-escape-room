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
 * ROOM 3 - THE INJECTION VAULT - golden master.
 *
 * <p><b>LOCKED FILE.</b> These tests pin down behaviour that already works today. They
 * pass before the escape and must still pass after it. If one of them goes red, the
 * refactoring broke the bank.
 */
@Tag("golden")
@DisplayName("Injection Vault: behaviour that must survive")
class VaultGoldenMasterTest {

    /** Five customers, ids 1 to 5. */
    private static CustomerDirectory seeded() {
        CustomerDirectory directory = RustyVault.openDirectory();
        directory.addCustomer("Anna", "Gdansk", 150000);
        directory.addCustomer("Bob", "Krakow", 20000);
        directory.addCustomer("Carl", "Gdansk", 90000);
        directory.addCustomer("Dora", "Warsaw", 20000);
        directory.addCustomer("O'Brien", "Cork", 5000);
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
    @DisplayName("customers get ids 1, 2, 3 and can be read back")
    void addAndGetById() {
        CustomerDirectory directory = RustyVault.openDirectory();
        assertEquals(1, directory.addCustomer("Anna", "Gdansk", 150000));
        assertEquals(2, directory.addCustomer("Bob", "Krakow", 0));
        assertEquals("1|Anna|Gdansk|150000", directory.getById("1"));
        assertEquals("2|Bob|Krakow|0", directory.getById("2"));
        assertNull(directory.getById("99"));
        assertEquals(2, directory.count());
    }

    @Test
    @DisplayName("an empty directory is empty")
    void emptyDirectory() {
        CustomerDirectory directory = RustyVault.openDirectory();
        assertEquals(0, directory.count());
        assertEquals(List.of(), directory.list("id", false));
        assertEquals(List.of(), directory.findByName("Anna"));
    }

    @Test
    @DisplayName("names are matched exactly, apostrophes included")
    void findByName() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("5|O'Brien|Cork|5000"), directory.findByName("O'Brien"));
        assertEquals(List.of(), directory.findByName("anna"));
        directory.addCustomer("Anna", "Poznan", 1);
        assertEquals(List.of("1", "6"), ids(directory.findByName("Anna")));
    }

    @Test
    @DisplayName("any characters are fine inside a name or a city")
    void strangeCharactersAreData() {
        CustomerDirectory directory = RustyVault.openDirectory();
        long id = directory.addCustomer("Zoe \"Z\"; DROP TABLE customer; --", "Lodz\\Centrum", 7);
        assertEquals(id + "|Zoe \"Z\"; DROP TABLE customer; --|Lodz\\Centrum|7",
                directory.getById(String.valueOf(id)));
        assertEquals(1, directory.findByName("Zoe \"Z\"; DROP TABLE customer; --").size());
    }

    @Test
    @DisplayName("a prefix search ignores case")
    void prefixSearch() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("1"), ids(directory.searchByNamePrefix("an")));
        assertEquals(List.of("1"), ids(directory.searchByNamePrefix("ANN")));
        assertEquals(List.of("2"), ids(directory.searchByNamePrefix("b")));
        assertEquals(List.of(), directory.searchByNamePrefix("Zed"));
    }

    @Test
    @DisplayName("an empty prefix matches everybody")
    void emptyPrefix() {
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(seeded().searchByNamePrefix("")));
    }

    @Test
    @DisplayName("lists can be sorted by id, name, city and balance, either way")
    void sorting() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(directory.list("id", false)));
        assertEquals(List.of("5", "4", "3", "2", "1"), ids(directory.list("id", true)));
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(directory.list("name", false)));
        assertEquals(List.of("5", "4", "3", "2", "1"), ids(directory.list("name", true)));
        assertEquals(List.of("5", "1", "3", "2", "4"), ids(directory.list("city", false)));
        assertEquals(List.of("4", "2", "1", "3", "5"), ids(directory.list("city", true)));
        assertEquals(List.of("5", "2", "4", "3", "1"), ids(directory.list("balance", false)));
        assertEquals(List.of("1", "3", "2", "4", "5"), ids(directory.list("balance", true)));
    }

    @Test
    @DisplayName("customers are found by id, in id order, each once")
    void findByIds() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("1", "3", "5"), ids(directory.findByIds(List.of("5", "1", "3", "1"))));
        assertEquals(List.of("2"), ids(directory.findByIds(List.of("2", "77"))));
        assertEquals(List.of(), directory.findByIds(List.of("77")));
    }

    @Test
    @DisplayName("richer than means strictly more")
    void richerThan() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("1", "3"), ids(directory.richerThan("20000")));
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(directory.richerThan("0")));
        assertEquals(List.of(), directory.richerThan("150000"));
    }

    @Test
    @DisplayName("neighbours are the others in the same city")
    void sameCity() {
        CustomerDirectory directory = seeded();
        assertEquals(List.of("3"), ids(directory.sameCityAs("1")));
        assertEquals(List.of("1"), ids(directory.sameCityAs("3")));
        assertEquals(List.of(), directory.sameCityAs("2"));
        assertThrows(IllegalArgumentException.class, () -> directory.sameCityAs("99"));
    }

    @Test
    @DisplayName("deleting a city removes its customers and says how many")
    void deleteByCity() {
        CustomerDirectory directory = seeded();
        assertEquals(2, directory.deleteByCity("Gdansk"));
        assertEquals(3, directory.count());
        assertEquals(0, directory.deleteByCity("Gdansk"));
        assertEquals(0, directory.deleteByCity("Nowhere"));
        assertEquals(3, directory.count());
    }

    @Test
    @DisplayName("a customer needs a name, a city and a balance that is not negative")
    void validation() {
        CustomerDirectory directory = RustyVault.openDirectory();
        assertThrows(IllegalArgumentException.class, () -> directory.addCustomer(" ", "Gdansk", 1));
        assertThrows(IllegalArgumentException.class, () -> directory.addCustomer(null, "Gdansk", 1));
        assertThrows(IllegalArgumentException.class, () -> directory.addCustomer("Anna", "", 1));
        assertThrows(IllegalArgumentException.class, () -> directory.addCustomer("Anna", null, 1));
        assertThrows(IllegalArgumentException.class, () -> directory.addCustomer("Anna", "Gdansk", -1));
        assertEquals(0, directory.count());
    }

    @Test
    @DisplayName("every directory is its own database")
    void directoriesAreIsolated() {
        CustomerDirectory first = RustyVault.openDirectory();
        first.addCustomer("Anna", "Gdansk", 1);
        CustomerDirectory second = RustyVault.openDirectory();
        assertEquals(0, second.count());
        assertEquals(1, second.addCustomer("Bob", "Krakow", 2));
        assertEquals(1, first.count());
    }
}
