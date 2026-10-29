// Deliberately bad code for testing the reviewer. Do not fix it!
import java.sql.*;
import java.util.*;

public class UserService {

    private static final String DB_PASSWORD = "admin123";
    private Connection conn;

    public User findUser(String username) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE name = '" + username + "'");
        if (rs.next()) {
            return new User(rs.getString("name"), rs.getString("email"));
        }
        return null;
    }

    public List<User> findActiveUsers(List<String> ids) throws SQLException {
        List<User> result = new ArrayList<>();
        for (String id : ids) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getBoolean("active")) {
                result.add(new User(rs.getString("name"), rs.getString("email")));
            }
        }
        return result;
    }

    public double averageAge(List<User> users) {
        int total = 0;
        for (User u : users) {
            total += u.getAge();
        }
        return total / users.size();
    }
}
