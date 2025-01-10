package rentasad.library.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AdsUtilityTest
{


	private static Connection adsCon;

	@BeforeAll
	protected static void setUp() throws Exception
	{
		createLalaTable();
	}

	@AfterAll
	protected static  void tearDown() throws Exception
	{
		dropLalaTable();
	}

	@Test
	public void testExistTableWithSpecialDirectory() throws SQLException
	{
		assertTrue(AdsUtility.existTable("GUSTINI", "Lala"));
		assertFalse(AdsUtility.existTable(adsCon, "Lala"));
		assertTrue(AdsUtility.existTable("GUSTINI", "LALA"));
		assertTrue(AdsUtility.existTable("GUSTINI", "lala"));
		assertTrue(AdsUtility.existTable("GUSTINI", "LaLa"));
		assertFalse(AdsUtility.existTable("GUSTINI", "LaLa2"));
		assertFalse(AdsUtility.existTable("GUSTINI", "LaL"));
	}

	@Test
	public void testExistTableWithSpecialConnection() throws SQLException
	{
		Connection conGustini = AdsConnection.dbConnectToDictionary("GUSTINI");
		assertTrue(AdsUtility.existTable(conGustini, "Lala"));
		assertFalse(AdsUtility.existTable(adsCon, "Lala"));
	}


	private static void createLalaTable()
	{
		try
		{
			adsCon = AdsConnection.dbConnect();
			String createTableQuery = "CREATE TABLE GUSTINI\\Lala (ID Numeric( 13 ,0 ));";
			Statement stmt = adsCon.createStatement();
			stmt.executeUpdate(createTableQuery);
		} catch (SQLException e)
		{
			if (!e.getMessage().equals("[iAnywhere Solutions][Advantage JDBC]State = S0000;   NativeError = 2010;  [SAP][Advantage SQL Engine][ISAM]ISAM table already exists"))
			{
				throw new RuntimeException(e);
			}
		}

	}

	private static void dropLalaTable() throws SQLException
	{
		adsCon = AdsConnection.dbConnect();
		String createTableQuery = "DROP TABLE GUSTINI\\Lala;";
		Statement stmt = adsCon.createStatement();
		stmt.executeUpdate(createTableQuery);
		adsCon.close();
	}
}
