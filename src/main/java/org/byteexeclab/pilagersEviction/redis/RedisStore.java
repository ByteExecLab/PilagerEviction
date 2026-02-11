package org.byteexeclab.pilagersEviction.redis;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RedisStore {
    private final JavaPlugin plugin;
    private JedisPooled jedis;

    public RedisStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("redis.enabled", false);
    }

    public String prefix() {
        return plugin.getConfig().getString("redis.keyPrefix", "clearedoutposts");
    }

    public void connect() {
        if (!isEnabled()) {
            plugin.getLogger().warning("Redis is disabled in config.yml; cleared zones will NOT persist.");
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        String host = cfg.getString("redis.host", "127.0.0.1");
        int port = cfg.getInt("redis.port", 6379);
        String password = cfg.getString("redis.password", "");
        int db = cfg.getInt("redis.database", 0);

        try {
            JedisClientConfig clientConfig;
            if (!password.isBlank()) {
                clientConfig = DefaultJedisClientConfig.builder()
                        .password(password)
                        .database(db)
                        .build();
            } else {
                clientConfig = DefaultJedisClientConfig.builder()
                        .database(db)
                        .build();
            }

            this.jedis = new JedisPooled(new HostAndPort(host, port), clientConfig);

            // quick ping
            String pong = jedis.ping();
            plugin.getLogger().info("Connected to Redis: " + host + ":" + port + " (db " + db + ") ping=" + pong);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Redis: " + e.getMessage());
            plugin.getLogger().severe("Redis persistence will NOT work until this is fixed.");
            this.jedis = null;
        }
    }

    public void close() {
        if (jedis != null) jedis.close();
    }

    private void requireJedis() {
        if (!isEnabled()) throw new IllegalStateException("Redis disabled");
        if (jedis == null) throw new IllegalStateException("Redis not connected");
    }

    public void saveZone(String zoneId, Map<String, String> fields) {
        requireJedis();
        String key = prefix() + ":zone:" + zoneId;
        jedis.hset(key, fields);
        jedis.sadd(prefix() + ":index", zoneId);
    }

    public Map<String, String> loadZone(String zoneId) {
        requireJedis();
        String key = prefix() + ":zone:" + zoneId;
        return jedis.hgetAll(key);
    }

    public Set<String> loadAllZoneIds() {
        requireJedis();
        return jedis.smembers(prefix() + ":index");
    }

    public Map<String, Map<String, String>> loadAllZones() {
        requireJedis();
        Set<String> ids = loadAllZoneIds();
        Map<String, Map<String, String>> out = new HashMap<>();
        for (String id : ids) {
            out.put(id, loadZone(id));
        }
        return out;
    }
}
