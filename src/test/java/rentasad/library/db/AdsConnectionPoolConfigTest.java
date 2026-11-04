package rentasad.library.db;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the pool configuration and the URL building. These tests need NO ADS:
 * they never open a connection and never read adsConnection.ini.
 */
public class AdsConnectionPoolConfigTest
{
	private static final String[] POOL_KEYS = { AdsConnection.PARAMETER_NAME_POOL_SIZE, AdsConnection.PARAMETER_NAME_MIN_IDLE, AdsConnection.PARAMETER_NAME_IDLE_TIMEOUT_SEC,
			AdsConnection.PARAMETER_NAME_MAX_LIFETIME_SEC };

	@AfterEach
	public void clearSystemProperties()
	{
		for (String key : POOL_KEYS)
		{
			System.clearProperty(AdsConnection.SYSTEM_PROPERTY_PREFIX + key);
		}
	}

	@Test
	public void defaultsWhenNothingConfigured()
	{
		AdsConnection.PoolSettings settings = AdsConnection.readPoolSettings(new HashMap<>());
		assertEquals(20, settings.poolSize());
		assertEquals(0, settings.minIdle());
		assertEquals(10_000L, settings.idleTimeoutMs());
		assertEquals(1_800_000L, settings.maxLifetimeMs());
	}

	@Test
	public void defaultsWhenConfigMapIsNull()
	{
		AdsConnection.PoolSettings settings = AdsConnection.readPoolSettings(null);
		assertEquals(AdsConnection.DEFAULT_POOL_SIZE, settings.poolSize());
		assertEquals(AdsConnection.DEFAULT_MIN_IDLE, settings.minIdle());
	}

	@Test
	public void valuesFromConfigMap()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_POOL_SIZE, "5");
		configMap.put(AdsConnection.PARAMETER_NAME_MIN_IDLE, "1");
		configMap.put(AdsConnection.PARAMETER_NAME_IDLE_TIMEOUT_SEC, " 15 ");
		configMap.put(AdsConnection.PARAMETER_NAME_MAX_LIFETIME_SEC, "600");
		AdsConnection.PoolSettings settings = AdsConnection.readPoolSettings(configMap);
		assertEquals(5, settings.poolSize());
		assertEquals(1, settings.minIdle());
		assertEquals(15_000L, settings.idleTimeoutMs());
		assertEquals(600_000L, settings.maxLifetimeMs());
	}

	@Test
	public void invalidValuesFallBackToDefaults()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_POOL_SIZE, "0");
		configMap.put(AdsConnection.PARAMETER_NAME_MIN_IDLE, "-1");
		configMap.put(AdsConnection.PARAMETER_NAME_IDLE_TIMEOUT_SEC, "ten");
		configMap.put(AdsConnection.PARAMETER_NAME_MAX_LIFETIME_SEC, "");
		AdsConnection.PoolSettings settings = AdsConnection.readPoolSettings(configMap);
		assertEquals(AdsConnection.DEFAULT_POOL_SIZE, settings.poolSize());
		assertEquals(AdsConnection.DEFAULT_MIN_IDLE, settings.minIdle());
		assertEquals(AdsConnection.DEFAULT_IDLE_TIMEOUT_SEC * 1000L, settings.idleTimeoutMs());
		assertEquals(AdsConnection.DEFAULT_MAX_LIFETIME_SEC * 1000L, settings.maxLifetimeMs());
	}

	@Test
	public void minIdleIsLimitedToPoolSize()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_POOL_SIZE, "3");
		configMap.put(AdsConnection.PARAMETER_NAME_MIN_IDLE, "7");
		AdsConnection.PoolSettings settings = AdsConnection.readPoolSettings(configMap);
		assertEquals(3, settings.poolSize());
		assertEquals(3, settings.minIdle());
	}

	@Test
	public void systemPropertyWinsOverConfigMap()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_POOL_SIZE, "5");
		System.setProperty(AdsConnection.SYSTEM_PROPERTY_PREFIX + AdsConnection.PARAMETER_NAME_POOL_SIZE, "7");
		assertEquals(7, AdsConnection.readPoolSettings(configMap).poolSize());
	}

	@Test
	public void hikariConfigUsesPoolSettings()
	{
		String url = "jdbc:extendedsystems:advantage://ads.example.test:6262/data/;LockType=proprietary;CharType=ansi;TableType=cdx";
		HikariConfig hikariConfig = AdsConnection.buildHikariConfig(url, new AdsConnection.PoolSettings(4, 0, 12_000L, 900_000L));
		assertEquals(url, hikariConfig.getJdbcUrl());
		assertEquals(AdsConnection.DRIVER_CLASS_NAME, hikariConfig.getDriverClassName());
		assertEquals(4, hikariConfig.getMaximumPoolSize());
		assertEquals(0, hikariConfig.getMinimumIdle());
		assertEquals(12_000L, hikariConfig.getIdleTimeout());
		assertEquals(900_000L, hikariConfig.getMaxLifetime());
		assertEquals("SELECT 1 FROM system.iota", hikariConfig.getConnectionTestQuery());
	}

	@Test
	public void connectionStringContainsDictionaryWithSuffix()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_HOST, "ads.example.test");
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_SOCKET, "6262");
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_DATABASE_DICTIONARY, "/data/" + "F99\\IMPORT");
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_LOCK_TYPE, "proprietary");
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_CHAR_TYPE, "OEM");
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_TABLE_TYPE, "cdx");
		assertEquals("jdbc:extendedsystems:advantage://ads.example.test:6262/data/F99\\IMPORT;LockType=proprietary;CharType=OEM;TableType=cdx",
					 AdsConnection.generateConnectionString(configMap));
	}

	@Test
	public void unpooledConnectionNeedsHostAndDictionary()
	{
		Map<String, String> configMap = new HashMap<>();
		configMap.put(AdsConnection.PARAMETER_NAME_ADS_DATABASE_DICTIONARY, "/data/");
		assertThrows(SQLException.class, () -> AdsConnection.dbConnectUnpooled(configMap));
		assertThrows(SQLException.class, () -> AdsConnection.dbConnectUnpooled((Map<String, String>) null));
	}
}
