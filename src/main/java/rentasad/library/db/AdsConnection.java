package rentasad.library.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import rentasad.library.configFileTool.ConfigFileTool;
import rentasad.library.configFileTool.ConfigFileToolException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
@Slf4j
public class AdsConnection
{
	public static final String ADS_CONNECTION_VERSION = "D3.0.2";
	public static final String DEFAULT_CONFIG_FILE_PATH = "resources/config/adsConnection.ini";
	public static final String DEFAULT_CONFIG_FILE_PATH_IN_RESOURCES = "config/adsConnection.ini";
	public static final String DEFAULT_SECTION_NAME = "ADS_CONNECTION";
	public static final String DRIVER_CLASS_NAME = "com.extendedsystems.jdbc.advantage.ADSDriver";
	public static final int POOL_SIZE = 8;

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
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDriverClassName(DRIVER_CLASS_NAME);
		hikariConfig.setJdbcUrl(connectionString);
		hikariConfig.setMaximumPoolSize(POOL_SIZE);
		hikariConfig.setPoolName("AdsConnectionPool-" + connectionString.hashCode());
		// To prevent an not implemented exception override setConnectionTestQuery
		// java.lang.AbstractMethodError: Receiver class com.extendedsystems.jdbc.advantage.ADSConnection does not define or inherit an implementation of the resolved method 'abstract boolean isValid(int)' of interface java.sql.Connection.
		hikariConfig.setConnectionTestQuery("SELECT 1 FROM system.iota");
		this.dataSource = new HikariDataSource(hikariConfig);
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
	private static String generateConnectionString(Map<String, String> configMap)
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
	private void initializeConfigMapWithDefaults(final Map<String, String> configMap) throws SQLException
	{
		Map<String, String> defaultConfigMap = getDefaultConfigMap();

		for (String parameter : List.of(PARAMETER_NAME_ADS_SOCKET, PARAMETER_NAME_ADS_LOCK_TYPE, PARAMETER_NAME_ADS_CHAR_TYPE, PARAMETER_NAME_ADS_TABLE_TYPE))
		{
			putDefaultIfAbsent(configMap, defaultConfigMap, parameter);
		}
	}

	/**
	 * Adds a key-value pair from the default map to the target map if the*/
	private void putDefaultIfAbsent(Map<String, String> targetMap, Map<String, String> defaultMap, String key)
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
