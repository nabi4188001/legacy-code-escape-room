package com.rustyvault.legacy;

import com.rustyvault.CustomerDirectory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * CUSTOMER DIRECTORY
 * CREATED 18.06.2003
 * CHG 02.02.2007 - quotes doubled after a customer called O'Neil broke the night run
 * CHG 11.10.2012 - sort order now comes from the screen, saves a case statement
 */
public class LegacyCustomerDirectory implements CustomerDirectory {

    private static final String COLUMNS = "id, name, city, balance_cents";

    private Connection conn;

    public LegacyCustomerDirectory(Connection conn) {
        this.conn = conn;
    }

    public long addCustomer(String name, String city, long balanceCents) {
        if (name == null || name.trim().length() == 0 || city == null
                || city.trim().length() == 0 || balanceCents < 0) {
            throw new IllegalArgumentException("INVALID CUSTOMER");
        }
        String sql = "INSERT INTO customer (name, city, balance_cents) VALUES ('"
                + quote(name) + "', '" + quote(city) + "', " + balanceCents + ")";
        try {
            Statement st = conn.createStatement();
            st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            ResultSet keys = st.getGeneratedKeys();
            keys.next();
            long id = keys.getLong(1);
            st.close();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("SQL ERROR: " + e.getMessage());
        }
    }

    public List<String> findByName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("INVALID NAME");
        }
        return query("SELECT " + COLUMNS + " FROM customer WHERE name = '" + quote(name)
                + "' ORDER BY id");
    }

    public List<String> searchByNamePrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("INVALID PREFIX");
        }
        return query("SELECT " + COLUMNS + " FROM customer WHERE UPPER(name) LIKE UPPER('"
                + prefix + "%') ORDER BY id");
    }

    public List<String> list(String sortBy, boolean descending) {
        String column = "balance".equals(sortBy) ? "balance_cents" : sortBy;
        return query("SELECT " + COLUMNS + " FROM customer ORDER BY " + column
                + (descending ? " DESC" : " ASC") + ", id ASC");
    }

    public String getById(String id) {
        List<String> rows = query("SELECT " + COLUMNS + " FROM customer WHERE id = " + id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<String> findByIds(List<String> ids) {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                in.append(",");
            }
            in.append(ids.get(i));
        }
        return query("SELECT " + COLUMNS + " FROM customer WHERE id IN (" + in
                + ") ORDER BY id");
    }

    public List<String> richerThan(String minBalanceCents) {
        return query("SELECT " + COLUMNS + " FROM customer WHERE balance_cents > "
                + minBalanceCents + " ORDER BY id");
    }

    public List<String> sameCityAs(String id) {
        String row = getById(id);
        if (row == null) {
            throw new IllegalArgumentException("UNKNOWN CUSTOMER: " + id);
        }
        String city = row.split("\\|")[2];
        return query("SELECT " + COLUMNS + " FROM customer WHERE city = '" + city
                + "' AND id <> " + id + " ORDER BY id");
    }

    public int deleteByCity(String city) {
        if (city == null) {
            throw new IllegalArgumentException("INVALID CITY");
        }
        try {
            Statement st = conn.createStatement();
            int deleted = st.executeUpdate("DELETE FROM customer WHERE city = '" + city + "'");
            st.close();
            return deleted;
        } catch (SQLException e) {
            throw new IllegalStateException("SQL ERROR: " + e.getMessage());
        }
    }

    public int count() {
        List<String> rows = query("SELECT " + COLUMNS + " FROM customer");
        return rows.size();
    }

    private List<String> query(String sql) {
        List<String> rows = new ArrayList<String>();
        try {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                rows.add(rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                        + rs.getLong(4));
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new IllegalStateException("SQL ERROR: " + e.getMessage());
        }
        return rows;
    }

    private String quote(String text) {
        return text.replace("'", "''");
    }
}
