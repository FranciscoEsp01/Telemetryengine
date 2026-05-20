// Requiere la librería SQLite JDBC (ej: sqlite-jdbc-3.45.1.0.jar) en el classpath del proyecto.

// DatabaseManager.java
package telemetryengine;

import java.sql.*;
import java.util.List;

public class DatabaseManager {
    private final String url = "jdbc:sqlite:telemetry.db";

    public DatabaseManager() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS session (id INTEGER PRIMARY KEY AUTOINCREMENT, car_name TEXT, start_time INTEGER, end_time INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS telemetry (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, timestamp INTEGER, speed_ms REAL, rpm REAL, gear INTEGER, FOREIGN KEY(session_id) REFERENCES session(id))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int createSession(String carName) {
        String sql = "INSERT INTO session(car_name, start_time) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, carName);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void endSession(int sessionId) {
        String sql = "UPDATE session SET end_time = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setInt(2, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertTelemetryBatch(int sessionId, List<TelemetryEngine.TelemetryPacket> packets) {
        if (packets.isEmpty()) return;
        String sql = "INSERT INTO telemetry(session_id, timestamp, speed_ms, rpm, gear) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (TelemetryEngine.TelemetryPacket p : packets) {
                pstmt.setInt(1, sessionId);
                pstmt.setLong(2, System.currentTimeMillis());
                pstmt.setFloat(3, p.speedMs());
                pstmt.setFloat(4, p.engineRpm());
                pstmt.setInt(5, p.gear());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String analyzeSession(int sessionId) {
        String sql = "SELECT MAX(speed_ms) * 3.6 AS max_speed, AVG(rpm) AS avg_rpm, COUNT(DISTINCT gear) AS unique_gears, COUNT(*) as data_points, " +
                     "(SELECT (end_time - start_time) / 1000.0 FROM session WHERE id = ?) AS duration_sec " +
                     "FROM telemetry WHERE session_id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sessionId);
            pstmt.setInt(2, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return String.format(
                        "Resumen de Carrera (Sesión %d):\n" +
                        "Tiempo en Pista: %.2f seg\n" +
                        "Velocidad Máxima: %.2f km/h\n" +
                        "Media de RPM: %.2f\n" +
                        "Marchas Totales Usadas: %d\n" +
                        "Registros de Telemetría: %d",
                        sessionId,
                        rs.getFloat("duration_sec"),
                        rs.getFloat("max_speed"),
                        rs.getFloat("avg_rpm"),
                        rs.getInt("unique_gears"),
                        rs.getInt("data_points")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "No hay datos suficientes para el análisis.";
    }
}