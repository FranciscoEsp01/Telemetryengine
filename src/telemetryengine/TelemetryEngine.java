// TelemetryEngine.java
package telemetryengine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelemetryEngine implements Runnable, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(TelemetryEngine.class.getName());
    private static final int BUFFER_SIZE = 2048; 
    private static final int BATCH_SIZE = 50; 
    
    private final int port;
    private final AtomicBoolean running;
    private final ExecutorService processorPool;
    private DatagramChannel channel;
    private final BiConsumer<TelemetryPacket, SessionStats> onPacketReceived;
    private final SessionStats sessionStats;
    
    private final DatabaseManager dbManager;
    private final int sessionId;
    private final List<TelemetryPacket> packetBuffer;

    public TelemetryEngine(int port, int threadPoolSize, BiConsumer<TelemetryPacket, SessionStats> onPacketReceived, String carName) {
        this.port = port;
        this.running = new AtomicBoolean(false);
        this.processorPool = Executors.newFixedThreadPool(threadPoolSize);
        this.onPacketReceived = onPacketReceived;
        this.sessionStats = new SessionStats();
        
        this.dbManager = new DatabaseManager();
        this.sessionId = this.dbManager.createSession(carName);
        this.packetBuffer = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            channel = DatagramChannel.open();
            channel.socket().bind(new InetSocketAddress(port));
            running.set(true);

            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN); 

            while (running.get()) {
                buffer.clear();
                channel.receive(buffer);
                buffer.flip();

                if (buffer.hasRemaining()) {
                    byte[] payload = new byte[buffer.remaining()];
                    buffer.get(payload);
                    processorPool.submit(() -> parseAndDispatch(payload));
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOGGER.log(Level.SEVERE, "Excepción en el canal UDP", e);
            }
        } finally {
            shutdown();
        }
    }

    private void parseAndDispatch(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN);
        
        if (buffer.remaining() >= 12) {
            float speedMs = buffer.getFloat();
            float engineRpm = buffer.getFloat();
            int currentGear = buffer.getInt();

            TelemetryPacket packet = new TelemetryPacket(speedMs, engineRpm, currentGear);
            sessionStats.register(packet);
            
            synchronized (packetBuffer) {
                packetBuffer.add(packet);
                if (packetBuffer.size() >= BATCH_SIZE) {
                    List<TelemetryPacket> batchToSave = new ArrayList<>(packetBuffer);
                    packetBuffer.clear();
                    Executors.newSingleThreadExecutor().submit(() -> dbManager.insertTelemetryBatch(sessionId, batchToSave));
                }
            }
            
            if (onPacketReceived != null) {
                onPacketReceived.accept(packet, sessionStats);
            }
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            processorPool.shutdown();
            
            synchronized (packetBuffer) {
                if (!packetBuffer.isEmpty()) {
                    dbManager.insertTelemetryBatch(sessionId, packetBuffer);
                }
            }
            dbManager.endSession(sessionId);
            
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public int getSessionId() {
        return sessionId;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    @Override
    public void close() {
        shutdown();
    }

    public static class TelemetryPacket {
        private final float speedMs;
        private final float engineRpm;
        private final int gear;

        public TelemetryPacket(float speedMs, float engineRpm, int gear) {
            this.speedMs = speedMs;
            this.engineRpm = engineRpm;
            this.gear = gear;
        }

        public float speedMs() { return speedMs; }
        public float getSpeedKmh() { return speedMs * 3.6f; }
        public float engineRpm() { return engineRpm; }
        public int gear() { return gear; }
    }

    public static class SessionStats {
        private float maxSpeedKmh = 0;
        private int gearChanges = 0;
        private double totalRpm = 0;
        private long readingsCount = 0;
        private int lastGear = -99;

        public synchronized void register(TelemetryPacket packet) {
            float currentSpeedKmh = packet.getSpeedKmh();
            if (currentSpeedKmh > maxSpeedKmh) {
                maxSpeedKmh = currentSpeedKmh;
            }
            
            if (lastGear != -99 && lastGear != packet.gear()) {
                gearChanges++;
            }
            lastGear = packet.gear();
            
            totalRpm += packet.engineRpm();
            readingsCount++;
        }

        public synchronized float getMaxSpeedKmh() { return maxSpeedKmh; }
        public synchronized int getGearChanges() { return gearChanges; }
        public synchronized float getAverageRpm() { return readingsCount == 0 ? 0 : (float) (totalRpm / readingsCount); }
    }
}