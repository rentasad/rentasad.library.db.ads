package rentasad.library.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import rentasad.library.configFileTool.ConfigFileTool;
import rentasad.library.configFileTool.ConfigFileToolException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gustini GmbH
 * Creation: 15.12.2015 Library gustini.library.db
 * Last Update: 14.11.2024
 * <p>
 * Changed from inline Connection String to Config File based Connection-String
 *
 * @author Matthias Staud
 * <p>
 * <p>
 * Description: Stellt eine Verbindung zur ADS-Datenbank her.
 */
@Log
public class AdsConnection
{
	public static final String ADS_CONNECTION_VERSION = "3.2.0";
	public static final String DEFAULT_CONFIG_FILE_PATH = "resources/config/adsConnection.ini";
	public static final String DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES = "config/adsConnection.ini";
	public static final String DEFAULT_SECTION_NAME = "ADS_CONNECTION";
	public static final String DRIVER_CLASS_NAME = "com.extendedsystems.jdbc.advantage.ADSDriver";

	/**
	 * Default maximum number of connections per pool (one pool per JDBC URL).
	 * Can be overridden with {@link #PARAMETER_NAME_POOL_SIZE}.
	 */
	public static final int DEFAULT_POOL_SIZE = 20;
	/**
	 * Default minimum number of idle connections per pool. With 0 the pool does not keep
	 * connections open that nobody uses, so the ADS can release the tables they had opened.
	 */
	public static final int DEFAULT_MIN_IDLE = 0;
	/**
	 * Default time in seconds after which an idle connection is closed physically.
	 * HikariCP enforces a minimum of 10 seconds and checks every 30 seconds.
	 */
	public static final int DEFAULT_IDLE_TIMEOUT_SEC = 10;
	/**
	 * Default maximum lifetime of a pooled connection in seconds (HikariCP default: 30 minutes).
	 */
	public static final int DEFAULT_MAX_LIFETIME_SEC = 1800;
	/**
	 * Smallest accepted idle timeout in seconds (HikariCP minimum); 0 means "never".
	 */
	public static final int MIN_IDLE_TIMEOUT_SEC = 10;
	/**
	 * Smallest accepted maximum lifetime in seconds (HikariCP minimum); 0 means "infinite".
	 */
	public static final int MIN_MAX_LIFETIME_SEC = 30;

	/**
	 * @deprecated since 3.2.0 the pool size is configurable, see {@link #PARAMETER_NAME_POOL_SIZE}
	 * and {@link #DEFAULT_POOL_SIZE}. Until 3.1.1 the value was 180.
	 */
	@Deprecated
	public static final int POOL_SIZE = DEFAULT_POOL_SIZE;

	/**
	 * Optional key in section ADS_CONNECTION: maximum number of connections per pool.
	 * Default: {@link #DEFAULT_POOL_SIZE}.
	 */
	public static final String PARAMETER_NAME_POOL_SIZE = "POOL_SIZE";
	/**
	 * Optional key in section ADS_CONNECTION: minimum number of idle connections per pool.
	 * Default: {@link #DEFAULT_MIN_IDLE}.
	 */
	public static final String PARAMETER_NAME_MIN_IDLE = "MIN_IDLE";
	/**
	 * Optional key in section ADS_CONNECTION: seconds until an idle connection is closed (0 = never).
	 * Default: {@link #DEFAULT_IDLE_TIMEOUT_SEC}.
	 */
	public static final String PARAMETER_NAME_IDLE_TIMEOUT_SEC = "IDLE_TIMEOUT_SEC";
	/**
	 * Optional key in section ADS_CONNECTION: maximum lifetime of a pooled connection in seconds (0 = infinite).
	 * Default: {@link #DEFAULT_MAX_LIFETIME_SEC}.
	 */
	public static final String PARAMETER_NAME_MAX_LIFETIME_SEC = "MAX_LIFETIME_SEC";
	/**
	 * Prefix for system properties that override the pool keys, e.g.
	 * <code>-Drentasad.library.db.ads.POOL_SIZE=5</code>. A system property wins over the config map.
	 */
	public static final String SYSTEM_PROPERTY_PREFIX = "rentasad.library.db.ads.";

	/**
	 * Pool settings as used for HikariCP.
	 *
	 * @param poolSize        maximum number of connections
	 * @param minIdle         minimum number of idle connections
	 * @param idleTimeoutMs   idle timeout in milliseconds (0 = never)
	 * @param maxLifetimeMs   maximum lifetime in milliseconds (0 = infinite)
	 */
	record PoolSettings(int poolSize, int minIdle, long idleTimeoutMs, long maxLifetimeMs)
	{
	}

	private static final Map<String, AdsConnection> instances = new HashMap<>();
	private String connectionString;
	/**
	 *
	 */
	public static final String PARAMETER_NAME_ADS_HOST = "ADS_HOST";
	/**
	 * Parameter fuer Verbindungsport zur Datenbank. Default-Value: 6262
	 */
	public static final String PARAMETER_NAME_ADS_SOCKET = "ADS_SOCKET";

	/**
	 * Type of locking to use. <br>
	 * The valid values for this property are "proprietary" or "compatible". <br>
	 * The default is "proprietary". <br>
	 * <p>
	 * If the application is to be used with non-Advantage applications, then
	 * "compatible" <br>
	 * locking should be used. If the table will be used only by Advantage
	 * applications, then <br>
	 * "proprietary" locking should be used. When the TableType property is "adt",
	 * this property <br>
	 * is ignored and "proprietary" locking is always used. When "compatible"
	 * locking is chosen, <br>
	 * Advantage uses the appropriate style based on the table type. See Advantage
	 * Locking Modes <br>
	 * for more information.<br>
	 */
	public static final String PARAMETER_NAME_ADS_LOCK_TYPE = "LOCK_TYPE";
	/**
	 * Type of character data in the table. <br>
	 * The valid values for this property are <br>
	 * "ansi", <br>
	 * "oem" or a known collation name, such as <br>
	 * GERMAN_VFP_CI_AS_1252. <br>
	 * See AdsSetCollation for additional information. <br>
	 * The default is "ansi". <br>
	 * <br>
	 * This property indicates the type of character data to be stored in the table
	 * and how comparisons of character strings are performed. <br>
	 * If the property is set to "ansi" or "oem", the default ansi or oem collation
	 * of the server will be used. <br>
	 * For compatibility with DOS-based CA-Clipper applications, "oem" should be
	 * specified. <br>
	 * When TableType property is "adt", oem collation is never used When opening a
	 * database table, i.e., <br>
	 * table that is part of the Advantage Data Dictionary specified in the Catalog
	 * property, this parameter is ignored.<br>
	 * <br>
	 * The Advantage Server will use the information stored in the data dictionary
	 * to resolve the character data type.<br>
	 */
	public static final String PARAMETER_NAME_ADS_CHAR_TYPE = "CHAR_TYPE";
	/**
	 * Type of table. The valid values for this property are "adt", "vfp", "cdx" or
	 * "ntx".<br>
	 * If the catalog property specifies the path of a Advantage Data
	 * Dictionary,<br>
	 * this property is ignored except for executing the SQL statement "CREATE TABLE
	 * ... ".<br>
	 * If the catalog property specifies a directory where free tables are
	 * located,<br>
	 * this property applies to tables used in all SQL Statements.<br>
	 */
	public static final String PARAMETER_NAME_ADS_TABLE_TYPE = "TABLE_TYPE";

	/**
	 * The location of the database can be specified after the <hostname:port> <br>
	 * portion of the connection URL or it can be specified using the catalog
	 * property. <br>
	 * The following two URLs connect to the same database in the 'userdata' share
	 * on 'server1'. <br>
	 * <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262/userdata/db1/db.add" ); <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262;catalog =
	 * //server1/userdata/db1/db.add" ); <br>
	 * <br>
	 * <br>
	 * Although UNC path is the preferred method for specifying the location of the
	 * database, it is possible to use the drive <br>
	 * letter notation on the server to make the connection. For example, if the
	 * 'userdata' share on the 'server1' is actually <br>
	 * "c:\\userdata" on a Microsoft Windows 2003 server, the following URL can be
	 * use to obtain the connection. <br>
	 * <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262;catalog =
	 * c:\\userdata\\db1\\db.add" ); <br>
	 * <br>
	 * To connect to the Advantage Database Server to use free tables, specify the
	 * directory path as the catalog or specify the <br>
	 * directory path after the <hostname:port>. The following three statements will
	 * make the equivalent connection. <br>
	 * <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262/userdata/db2" ); <br>
	 * <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262;catalog =
	 * //server1/userdata/db2" ); <br>
	 * <br>
	 * <br>
	 * conn = DriverManager.getConnection (
	 * "jdbc:extendedsystems:advantage://server1:6262;catalog = c:\\userdata\\db2"
	 * ); <br>
	 * <br>
	 * <br>
	 * Note that when connecting to the Internet port of the Advantage Database
	 * Server, free table connection is not allowed. <br>
	 * An Advantage Data Dictionary must be used to authenticate the user. <br>
	 * <br>
	 * <br>
	 */
	public static final String PARAMETER_NAME_ADS_DATABASE_DICTIONARY = "DATABASE_DICTIONARY";

	private Map<String, String> configMap;
	private HikariDataSource dataSource;
	private static AdsConnection instance;

	/**
	 * @param configMap if should contain the following keys:
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_HOST</code>
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_SOCKET  (optional)</code>
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_DATABASE_DICTIONARY</code>
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_LOCK_TYPE</code>
	 *                  (optional)
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_CHAR_TYPE</code>
	 *                  (optional)
	 *                  <code>gustini.library.db.AdsConnection.PARAMETER_NAME_ADS_TABLE_TYPE</code>
	 *                  (optional)
	 *                  <p>
	 *                  of optional parameters not given, it was used default value.
	 * @throws SQLException
	 */
	private AdsConnection(final Map<String, String> configMap) throws SQLException
	{
		if (isConfigMapValid(configMap))
		{
			initializeConfigMapWithDefaults(configMap);
			this.configMap = configMap;
			this.connectionString = generateConnectionString(configMap);
			initializeDataSource(configMap);
		}
	}

	/**
	 * Initializes the data source using the provided configuration map.
	 * Configures a HikariDataSource instance with appropriate settings
	 * such as driver class name, connection URL, pool size, and pool name.
	 *
	 * @param configMap a map containing configuration key-value pairs used
	 *                  to generate the connection string and set additional properties
	 *                  for the data source initialization.
	 */
	private void initializeDataSource(Map<String, String> configMap)
	{
		HikariConfig hikariConfig = buildHikariConfig(connectionString, readPoolSettings(configMap));
		this.dataSource = new HikariDataSource(hikariConfig);
	}

	/**
	 * Builds the HikariCP configuration without opening a connection.
	 *
	 * @param connectionString the JDBC URL
	 * @param poolSettings     the pool settings
	 * @return the HikariCP configuration
	 */
	static HikariConfig buildHikariConfig(final String connectionString, final PoolSettings poolSettings)
	{
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDriverClassName(DRIVER_CLASS_NAME);
		hikariConfig.setJdbcUrl(connectionString);
		hikariConfig.setMaximumPoolSize(poolSettings.poolSize());
		hikariConfig.setMinimumIdle(poolSettings.minIdle());
		hikariConfig.setIdleTimeout(poolSettings.idleTimeoutMs());
		hikariConfig.setMaxLifetime(poolSettings.maxLifetimeMs());
		hikariConfig.setPoolName("AdsConnectionPool-" + connectionString.hashCode());
		// To prevent an not implemented exception override setConnectionTestQuery
		// java.lang.AbstractMethodError: Receiver class com.extendedsystems.jdbc.advantage.ADSConnection does not define or inherit an implementation of the resolved method 'abstract boolean isValid(int)' of interface java.sql.Connection.
		hikariConfig.setConnectionTestQuery("SELECT 1 FROM system.iota");
		return hikariConfig;
	}

	/**
	 * Reads the pool settings. Order: system property ({@link #SYSTEM_PROPERTY_PREFIX} + key),
	 * then the config map, then the default. Invalid values are logged and replaced by the default.
	 *
	 * @param configMap the configuration map (may be null)
	 * @return the pool settings
	 */
	static PoolSettings readPoolSettings(final Map<String, String> configMap)
	{
		int poolSize = readIntSetting(configMap, PARAMETER_NAME_POOL_SIZE, DEFAULT_POOL_SIZE, 1);
		int minIdle = readIntSetting(configMap, PARAMETER_NAME_MIN_IDLE, DEFAULT_MIN_IDLE, 0);
		if (minIdle > poolSize)
		{
			log.warning(String.format("%s=%d is greater than %s=%d, using %d", PARAMETER_NAME_MIN_IDLE, minIdle, PARAMETER_NAME_POOL_SIZE, poolSize, poolSize));
			minIdle = poolSize;
		}
		int idleTimeoutSec = readIntSetting(configMap, PARAMETER_NAME_IDLE_TIMEOUT_SEC, DEFAULT_IDLE_TIMEOUT_SEC, 0);
		idleTimeoutSec = zeroOrAtLeast(PARAMETER_NAME_IDLE_TIMEOUT_SEC, idleTimeoutSec, MIN_IDLE_TIMEOUT_SEC, DEFAULT_IDLE_TIMEOUT_SEC);
		int maxLifetimeSec = readIntSetting(configMap, PARAMETER_NAME_MAX_LIFETIME_SEC, DEFAULT_MAX_LIFETIME_SEC, 0);
		maxLifetimeSec = zeroOrAtLeast(PARAMETER_NAME_MAX_LIFETIME_SEC, maxLifetimeSec, MIN_MAX_LIFETIME_SEC, DEFAULT_MAX_LIFETIME_SEC);
		return new PoolSettings(poolSize, minIdle, TimeUnit.SECONDS.toMillis(idleTimeoutSec), TimeUnit.SECONDS.toMillis(maxLifetimeSec));
	}

	/**
	 * HikariCP silently replaces an idleTimeout below 10 s by 10 minutes and a maxLifetime below 30 s by
	 * 30 minutes, i.e. the opposite of a small value. Such values are therefore rejected here.
	 */
	private static int zeroOrAtLeast(final String key, final int value, final int minimumValue, final int defaultValue)
	{
		if (value != 0 && value < minimumValue)
		{
			log.warning(String.format("Invalid value %s=%d (0 or at least %d), using default %d", key, value, minimumValue, defaultValue));
			return defaultValue;
		}
		return value;
	}

	private static int readIntSetting(final Map<String, String> configMap, final String key, final int defaultValue, final int minimumValue)
	{
		String value = System.getProperty(SYSTEM_PROPERTY_PREFIX + key);
		if (value == null && configMap != null)
		{
			value = configMap.get(key);
		}
		if (value == null || value.isBlank())
		{
			return defaultValue;
		}
		try
		{
			int intValue = Integer.parseInt(value.trim());
			if (intValue < minimumValue)
			{
				log.warning(String.format("Invalid value %s=%s (minimum %d), using default %d", key, value, minimumValue, defaultValue));
				return defaultValue;
			}
			return intValue;
		} catch (NumberFormatException e)
		{
			log.warning(String.format("Invalid value %s=%s (no integer), using default %d", key, value, defaultValue));
			return defaultValue;
		}
	}

	public static AdsConnection getInstance(Map<String, String> configMap) throws SQLException
	{
		String connectionString = generateConnectionString(configMap);
		synchronized (instances)
		{
			if (!instances.containsKey(connectionString))
			{
				instances.put(connectionString, new AdsConnection(configMap));
			}
		}
		return instances.get(connectionString);
	}

	/**
	 * Generate the JDBC connection string from the config map.
	 * This ensures compatibility with the previous structure.
	 *
	 * @param configMap the configuration map with connection details.
	 * @return the generated JDBC connection string.
	 */
	static String generateConnectionString(Map<String, String> configMap)
	{
		String host = configMap.getOrDefault(PARAMETER_NAME_ADS_HOST, "");
		String socket = configMap.getOrDefault(PARAMETER_NAME_ADS_SOCKET, "");
		String databaseDictionary = configMap.getOrDefault(PARAMETER_NAME_ADS_DATABASE_DICTIONARY, "");
		String lockType = configMap.getOrDefault(PARAMETER_NAME_ADS_LOCK_TYPE, "proprietary");
		String charType = configMap.getOrDefault(PARAMETER_NAME_ADS_CHAR_TYPE, "ansi");
		String tableType = configMap.getOrDefault(PARAMETER_NAME_ADS_TABLE_TYPE, "adt");

		return String.format("jdbc:extendedsystems:advantage://%s:%s%s;LockType=%s;CharType=%s;TableType=%s", host, socket, databaseDictionary, lockType, charType, tableType);
	}

	/**
	 * Initializes the provided configuration map with default values for specific parameters,
	 * if those parameters are not already present in the map.
	 *
	 * @param configMap the configuration map to be initialized with default values. This map should
	 *                  already exist and can contain pre-defined configuration parameters.
	 * @throws SQLException if an SQL error occurs while retrieving the default configuration values.
	 */
	private static void initializeConfigMapWithDefaults(final Map<String, String> configMap) throws SQLException
	{
		initializeConfigMapWithDefaults(configMap, getDefaultConfigMap());
	}

	private static void initializeConfigMapWithDefaults(final Map<String, String> configMap, final Map<String, String> defaultConfigMap)
	{
		for (String parameter : List.of(PARAMETER_NAME_ADS_SOCKET, PARAMETER_NAME_ADS_LOCK_TYPE, PARAMETER_NAME_ADS_CHAR_TYPE, PARAMETER_NAME_ADS_TABLE_TYPE))
		{
			putDefaultIfAbsent(configMap, defaultConfigMap, parameter);
		}
	}

	/**
	 * Adds a key-value pair from the default map to the target map if the*/
	private static void putDefaultIfAbsent(Map<String, String> targetMap, Map<String, String> defaultMap, String key)
	{
		if (!targetMap.containsKey(key))
		{
			targetMap.put(key, defaultMap.get(key));
		}
	}

	public boolean isConfigMapValid(final Map<String, String> configMap)
	{
		boolean hostIsAvailable = configMap.containsKey(PARAMETER_NAME_ADS_HOST);
		boolean dictionaryIsAvailable = configMap.containsKey(PARAMETER_NAME_ADS_DATABASE_DICTIONARY);
		return hostIsAvailable && dictionaryIsAvailable;
	}

	/**
	 * Description: Stellt Verbindung unter Verwendung der uebergebenen
	 * Konfiguration her
	 *
	 * @return Creation: 15.12.2015 by mst
	 * @throws SQLException
	 */
	/**
	 * Returns the number of currently active (busy) connections in the Hikari connection pool.
	 *
	 * @return The number of active connections.
	 */
	public int getActiveConnectionCount()
	{
		if (this.dataSource != null && !this.dataSource.isClosed())
		{
			return this.dataSource.getHikariPoolMXBean().getActiveConnections();
		}
		return 0;
	}

	/**
	 * Returns the total number of connections in the Hikari connection pool (active and idle).
	 *
	 * @return The total number of connections.
	 */
	public int getTotalConnectionCount()
	{
		if (this.dataSource != null && !this.dataSource.isClosed())
		{
			return this.dataSource.getHikariPoolMXBean().getTotalConnections();
		}
		return 0;
	}

	/**
	 * Closes the Hikari connection pool, releasing all physical connections to the ADS database.
	 * This allows the underlying ADS DBF files to be released.
	 * The instance is removed from the internal cache to ensure it is re-initialized on the next request.
	 */
	public void closePool()
	{
		if (this.dataSource != null && !this.dataSource.isClosed())
		{
			this.dataSource.close();
		}
		synchronized (instances)
		{
			instances.remove(this.connectionString);
		}
	}

	/**
	 * Releases the connections of this pool without aborting work in progress
	 * (HikariCP <code>softEvictConnections()</code>): idle connections are closed physically at once,
	 * connections in use are closed when they are returned to the pool. The pool stays usable.
	 * <p>
	 * Unlike {@link #closePool()} this is safe while other threads use the same pool.
	 */
	public void releaseIdleConnections()
	{
		if (this.dataSource != null && !this.dataSource.isClosed())
		{
			HikariPoolMXBean poolMXBean = this.dataSource.getHikariPoolMXBean();
			if (poolMXBean != null)
			{
				poolMXBean.softEvictConnections();
			}
		}
	}

	/**
	 * Calls {@link #releaseIdleConnections()} for every pool created so far. Creates no new pool.
	 */
	public static void releaseIdleConnectionsOfAllPools()
	{
		List<AdsConnection> pools;
		synchronized (instances)
		{
			pools = new ArrayList<>(instances.values());
		}
		for (AdsConnection pool : pools)
		{
			try
			{
				pool.releaseIdleConnections();
			} catch (RuntimeException e)
			{
				log.log(Level.WARNING, "Could not release connections of pool " + pool.connectionString, e);
			}
		}
	}

	/**
	 * Checks if a connection is still valid.
	 *
	 * @param con the connection to check.
	 * @return true if the connection is valid, false otherwise.
	 */
	public static boolean isConnectionValid(Connection con)
	{
		if (con == null)
		{
			return false;
		}
		try
		{
			if (con.isClosed())
			{
				return false;
			}
			// Attempt a simple query to verify connection
			try (java.sql.Statement stmt = con.createStatement())
			{
				stmt.executeQuery("SELECT 1 FROM system.iota").close();
				return true;
			}
		} catch (SQLException | RuntimeException e)
		{
			return false;
		}
	}

	/**
	 * Ensures the provided connection is still valid or retrieves a new one from the pool.
	 *
	 * @param con the connection to validate.
	 * @return the same connection if it is valid, or a new connection if it was invalid or null.
	 * @throws SQLException if a new connection cannot be established.
	 */
	public static Connection getValidConnection(Connection con) throws SQLException
	{
		if (isConnectionValid(con))
		{
			return con;
		}
		return dbConnect();
	}

	public Connection getConnection() throws SQLException
	{
		if (dataSource == null)
		{
			throw new SQLException("DataSource is not initialized.");
		}
		return dataSource.getConnection();
	}

	/**
	 * Retrieves the default configuration map for AdsConnection.
	 *
	 * @return The default configuration map.
	 * @throws SQLException if an error occurs while retrieving the configuration map.
	 */
	public static Map<String, String> getDefaultConfigMap() throws SQLException
	{
		System.out.printf("AdsConnection %s%n", ADS_CONNECTION_VERSION);
		Map<String, String> configMap = null;
		try
		{
			Path defaultConfigFilePath = Paths.get(DEFAULT_CONFIG_FILE_PATH);
			if (Files.exists(defaultConfigFilePath))
			{
				System.out.println("read Configmap from local path_: " + DEFAULT_CONFIG_FILE_PATH);
				configMap = ConfigFileTool.readConfiguration(DEFAULT_CONFIG_FILE_PATH, DEFAULT_SECTION_NAME);
			}
			else if (AdsConnection.class.getResource("/" + DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES) != null)
			{
				System.out.println("read Configmap from resources: " + DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES);
				configMap = ConfigFileTool.readConfigurationFromResources(DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES, DEFAULT_SECTION_NAME);
			}
			else
			{
				String errorText = String.format("Config File for Ads connection not found: \n %s \n\n In Resources is also no config file found: %s", DEFAULT_CONFIG_FILE_PATH,
												 DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES);
				System.out.println(errorText);
				throw new FileNotFoundException(errorText);
			}
		 configMap.put(PARAMETER_NAME_ADS_HOST, configMap.get(PARAMETER_NAME_ADS_HOST));
	} catch(IOException |
	ConfigFileToolException e)

	{
		throw new SQLException(e);
	}

		return new HashMap<>(configMap);
}

/**
 * Description:
 *
 * @param configMap Creation: 15.12.2015 by mst
 * @throws SQLException
 */
public static Connection dbConnect(Map<String, String> configMap) throws SQLException
{
	AdsConnection instance = getInstance(configMap);
	return instance.getConnection();

}

/**
 * Description: Stellt eine Datenbankverbindung zur ADS-Datenbank her unter
 * Verwendung der getDefaultConfigMap();
 *
 * @return Creation: 15.12.2015 by mst
 * @throws SQLException
 * @throws ClassNotFoundException
 * @throws IllegalAccessException
 * @throws InstantiationException
 */
public static Connection dbConnect() throws SQLException
{
	return dbConnect(getDefaultConfigMap());
}

/**
 * Description: Stellt zu einem bestimmten Datenverzeichnis Verbindung her
 * ergaenzend zum DefaultVerbindungspfad
 *
 * @return
 * @throws SQLException
 */
public static Connection dbConnectToDictionary(final String appendConnectionDirectoryString) throws SQLException
{
	Map<String, String> configMap = new HashMap<>(getDefaultConfigMap());
	configMap.compute(PARAMETER_NAME_ADS_DATABASE_DICTIONARY, (k, dataDictionary) -> (dataDictionary == null ? "" : dataDictionary) + appendConnectionDirectoryString);
	AdsConnection instance = getInstance(configMap);
	return instance.getConnection();
}

/**
 * Opens a physical connection with the default configuration that does NOT come from a pool.
 * {@link Connection#close()} disconnects from the ADS, so the server releases all tables
 * this connection has opened (including tables held by cached cursors).
 *
 * @return a new, not pooled connection; the caller must close it
 * @throws SQLException if the configuration cannot be read or the connection fails
 */
public static Connection dbConnectUnpooled() throws SQLException
{
	return dbConnectUnpooled("");
}

/**
 * Like {@link #dbConnectToDictionary(String)}, but returns a physical connection that does NOT come
 * from a pool. {@link Connection#close()} disconnects from the ADS, so the server releases all tables
 * this connection has opened (including tables held by cached cursors).
 *
 * @param appendConnectionDirectoryString directory appended to the default DATABASE_DICTIONARY, e.g. <code>F01\IMPORT</code>
 * @return a new, not pooled connection; the caller must close it
 * @throws SQLException if the configuration cannot be read or the connection fails
 */
public static Connection dbConnectUnpooled(final String appendConnectionDirectoryString) throws SQLException
{
	Map<String, String> defaultConfigMap = getDefaultConfigMap();
	Map<String, String> configMap = new HashMap<>(defaultConfigMap);
	String suffix = appendConnectionDirectoryString == null ? "" : appendConnectionDirectoryString;
	configMap.compute(PARAMETER_NAME_ADS_DATABASE_DICTIONARY, (k, dataDictionary) -> (dataDictionary == null ? "" : dataDictionary) + suffix);
	// the default configuration is read only once per call
	return openUnpooledConnection(configMap, defaultConfigMap);
}

/**
 * Opens a physical connection for the given configuration that does NOT come from a pool.
 * Missing optional keys are filled from the default configuration, as for pooled connections.
 *
 * @param configMap configuration (ADS_HOST and DATABASE_DICTIONARY required)
 * @return a new, not pooled connection; the caller must close it
 * @throws SQLException if the configuration is invalid or the connection fails
 */
public static Connection dbConnectUnpooled(final Map<String, String> configMap) throws SQLException
{
	if (configMap == null || !configMap.containsKey(PARAMETER_NAME_ADS_HOST) || !configMap.containsKey(PARAMETER_NAME_ADS_DATABASE_DICTIONARY))
	{
		throw new SQLException(String.format("Config map must contain %s and %s", PARAMETER_NAME_ADS_HOST, PARAMETER_NAME_ADS_DATABASE_DICTIONARY));
	}
	return openUnpooledConnection(configMap, getDefaultConfigMap());
}

/**
 * Fills missing optional keys exactly like the pool constructor does and opens a physical connection.
 */
private static Connection openUnpooledConnection(final Map<String, String> configMap, final Map<String, String> defaultConfigMap) throws SQLException
{
	if (configMap == null || !configMap.containsKey(PARAMETER_NAME_ADS_HOST) || !configMap.containsKey(PARAMETER_NAME_ADS_DATABASE_DICTIONARY))
	{
		throw new SQLException(String.format("Config map must contain %s and %s", PARAMETER_NAME_ADS_HOST, PARAMETER_NAME_ADS_DATABASE_DICTIONARY));
	}
	Map<String, String> completeConfigMap = new HashMap<>(configMap);
	initializeConfigMapWithDefaults(completeConfigMap, defaultConfigMap);
	String unpooledConnectionString = generateConnectionString(completeConfigMap);
	try
	{
		Class.forName(DRIVER_CLASS_NAME);
	} catch (ClassNotFoundException e)
	{
		throw new SQLException("ADS JDBC driver not found: " + DRIVER_CLASS_NAME, e);
	}
	return DriverManager.getConnection(unpooledConnectionString);
}

public static Connection dbConnectToUrl(String fullConnectionUrl) throws SQLException
{
	Map<String, String> configMap = getDefaultConfigMap();
	String lockType = configMap.get(PARAMETER_NAME_ADS_LOCK_TYPE);
	String charType = configMap.get(PARAMETER_NAME_ADS_CHAR_TYPE);
	String tableType = configMap.get(PARAMETER_NAME_ADS_TABLE_TYPE);
	String dsnProperties = String.format(";LockType=%s;CharType=%s;TableType=%s", lockType, charType, tableType);
	String connectionString = String.format("%s%s", fullConnectionUrl, dsnProperties);
	try
	{
		if (instance == null)
		{
			instance = new AdsConnection(configMap);
		}
		return instance.getConnection();
	} catch (SQLException e)
	{
		throw new SQLException(e);
	}
}

/**
 * Establishes a database connection for a given mandant (tenant) by modifying the default
 * configuration parameters to include the specified mandant string.
 *
 * @param vs4MandantString the specific mandant (tenant) string to be appended to the data dictionary path.
 * @return a {@link Connection} object to the database configured for the given mandant.
 * @throws SQLException if a database access error occurs.
 */
public static Connection dbConnectToMandant(String vs4MandantString) throws SQLException
{
	Map<String, String> configMap = getDefaultConfigMap();
	configMap.compute(PARAMETER_NAME_ADS_DATABASE_DICTIONARY, (k, dataDictionary) -> dataDictionary + vs4MandantString);
	return dbConnect(configMap);
}

}
