// TelemetryDashboard.java
package telemetryengine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class TelemetryDashboard extends JFrame {

    private final JLabel speedLabel;
    private final JLabel rpmLabel;
    private final JLabel gearLabel;
    private final JProgressBar rpmBar;
    
    private final JLabel maxSpeedLabel;
    private final JLabel avgRpmLabel;
    private final JLabel gearChangesLabel;
    
    private TelemetryEngine engine;

    public TelemetryDashboard() {
        setTitle("Telemetry Monitor & Analytics");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(10, 10, 10));
        setLayout(new BorderLayout());

        JPanel livePanel = new JPanel();
        livePanel.setLayout(new GridLayout(3, 1, 0, 15));
        livePanel.setBackground(new Color(10, 10, 10));
        livePanel.setBorder(new EmptyBorder(20, 40, 20, 40));

        speedLabel = createLabel("0 km/h", 54, new Color(237, 237, 237));
        gearLabel = createLabel("N", 48, new Color(150, 150, 150));
        rpmLabel = createLabel("0 RPM", 24, new Color(150, 150, 150));

        rpmBar = new JProgressBar(0, 8000);
        rpmBar.setValue(0);
        rpmBar.setStringPainted(false);
        rpmBar.setForeground(new Color(94, 106, 210)); 
        rpmBar.setBackground(new Color(30, 30, 30));
        rpmBar.setBorderPainted(false);
        rpmBar.setPreferredSize(new Dimension(400, 8));

        JPanel rpmPanel = new JPanel(new BorderLayout(0, 10));
        rpmPanel.setBackground(new Color(10, 10, 10));
        rpmPanel.add(rpmLabel, BorderLayout.NORTH);
        rpmPanel.add(rpmBar, BorderLayout.CENTER);

        livePanel.add(speedLabel);
        livePanel.add(gearLabel);
        livePanel.add(rpmPanel);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(15, 15, 15));

        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new GridLayout(1, 3, 10, 0));
        statsPanel.setBackground(new Color(15, 15, 15));
        TitledBorder border = BorderFactory.createTitledBorder(new EmptyBorder(10, 10, 10, 10), "MÉTRICAS EN VIVO");
        border.setTitleColor(new Color(100, 100, 100));
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        statsPanel.setBorder(border);

        maxSpeedLabel = createLabel("Max Vel: 0 km/h", 14, new Color(200, 200, 200));
        avgRpmLabel = createLabel("Media RPM: 0", 14, new Color(200, 200, 200));
        gearChangesLabel = createLabel("Cambios: 0", 14, new Color(200, 200, 200));

        statsPanel.add(maxSpeedLabel);
        statsPanel.add(avgRpmLabel);
        statsPanel.add(gearChangesLabel);
        
        JButton btnAnalyze = new JButton("Finalizar y Analizar Datos");
        btnAnalyze.setBackground(new Color(40, 40, 40));
        btnAnalyze.setForeground(Color.WHITE);
        btnAnalyze.setFocusPainted(false);
        btnAnalyze.addActionListener(e -> runAnalysis());

        bottomPanel.add(statsPanel, BorderLayout.CENTER);
        bottomPanel.add(btnAnalyze, BorderLayout.SOUTH);

        add(livePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    public void setEngine(TelemetryEngine engine) {
        this.engine = engine;
    }

    private void runAnalysis() {
        if (engine != null) {
            engine.shutdown();
            String analysisResult = engine.getDbManager().analyzeSession(engine.getSessionId());
            JOptionPane.showMessageDialog(this, analysisResult, "Análisis de Datos", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }
    }

    private JLabel createLabel(String text, int size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        return label;
    }

    public void updateTelemetry(TelemetryEngine.TelemetryPacket packet, TelemetryEngine.SessionStats stats) {
        SwingUtilities.invokeLater(() -> {
            speedLabel.setText(String.format("%.0f km/h", packet.getSpeedKmh()));
            rpmLabel.setText(String.format("%.0f RPM", packet.engineRpm()));
            rpmBar.setValue((int) packet.engineRpm());
            
            String gearStr = packet.gear() == 0 ? "N" : packet.gear() == -1 ? "R" : String.valueOf(packet.gear());
            gearLabel.setText(gearStr);

            maxSpeedLabel.setText(String.format("Max Vel: %.0f km/h", stats.getMaxSpeedKmh()));
            avgRpmLabel.setText(String.format("Media RPM: %.0f", stats.getAverageRpm()));
            gearChangesLabel.setText(String.format("Cambios: %d", stats.getGearChanges()));
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TelemetryDashboard dashboard = new TelemetryDashboard();
            dashboard.setLocationRelativeTo(null);
            dashboard.setVisible(true);

            int port = 9996;
            int threads = Runtime.getRuntime().availableProcessors();
            String auto = "Porsche 911 GT3 R"; 
            
            TelemetryEngine engine = new TelemetryEngine(port, threads, dashboard::updateTelemetry, auto);
            dashboard.setEngine(engine);
            
            Thread engineThread = new Thread(engine, "UDP-Telemetry-Listener");
            engineThread.setDaemon(true);
            engineThread.start();
        });
    }
}