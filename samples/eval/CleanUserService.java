package com.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

/** The same features as samples/UserService.java, written carefully. Used to measure false positives. */
public class CleanUserService {

    private final DataSource dataSource;

    public CleanUserService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<User> findUser(String username) throws SQLException {
        String sql = "SELECT name, email FROM users WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new User(rs.getString("name"), rs.getString("email"))) : Optional.empty();
            }
        }
    }

    public List<User> findActiveUsers(List<String> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT name, email FROM users WHERE active = TRUE AND id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            List<User> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new User(rs.getString("name"), rs.getString("email")));
                }
            }
            return result;
        }
    }

    public Optional<BigDecimal> averageAge(List<User> users) {
        if (users.isEmpty()) {
            return Optional.empty();
        }
        long total = 0;
        for (User u : users) {
            total += u.getAge();
        }
        return Optional.of(BigDecimal.valueOf(total).divide(BigDecimal.valueOf(users.size()), 2, RoundingMode.HALF_UP));
    }
}
