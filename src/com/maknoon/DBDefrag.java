package com.maknoon;

import java.io.File;
import java.sql.*;

public final class DBDefrag
{
    public static void main(String[] args)
    {
        new DBDefrag();
    }

    private DBDefrag()
    {
        System.out.println("Make sure that the program is not running while defragmenting the database");
        try
        {
            final String programFolder = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + "/";

            Connection con;
            if (ArabicIndexer.derbyInUse)
            {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                con = DriverManager.getConnection("jdbc:derby:" + programFolder + "db");
            }
            else
            {
                Class.forName("org.h2.Driver");
                con = DriverManager.getConnection("jdbc:h2:" + programFolder + "db/indexerDatabase");
            }

            if (ArabicIndexer.derbyInUse)
            {
                // This CALL will retain storage of the unused deleted rows to OS which will reduce the size of the DB.
                // Other option was: CALL SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE('ROOT', 'CONTENTS', 1, 1, 1) but it didn't save anything [http://download.oracle.com/javadb/10.6.1.0/adminguide/cadminspace21579.html]
                final Statement stmt = con.createStatement();
                final ResultSet rs = stmt.executeQuery("SELECT schemaname, tablename FROM sys.sysschemas s, sys.systables t WHERE s.schemaid = t.schemaid AND t.tabletype = 'T'");
                final CallableStatement cs = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, 1)");
                while (rs.next())
                {
                    cs.setString(1, rs.getString("schemaname"));
                    cs.setString(2, rs.getString("tablename"));
                    cs.execute();
                }

                try
                {
                    DriverManager.getConnection("jdbc:derby:;shutdown=true");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                final Statement s = con.createStatement();
                s.execute("SHUTDOWN DEFRAG");
                //s.execute("SHUTDOWN COMPACT"); Not useful
            }

            /* Optimise Index as well
			final IndexWriter arabicIndexWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicIndex").toPath()), new IndexWriterConfig(arabicAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
			final IndexWriter arabicRootsWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicRootsIndex").toPath()), new IndexWriterConfig(arabicRootsAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
			final IndexWriter arabicLuceneWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicLuceneIndex").toPath()), new IndexWriterConfig(arabicLuceneAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
			final IndexWriter englishIndexWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "englishIndex").toPath()), new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

			// Version 1.7, Deprecated as of Lucene 3.5.0
			arabicIndexWriter.optimize();
            arabicRootsWriter.optimize();
            arabicLuceneWriter.optimize();
            englishIndexWriter.optimize();

            arabicIndexWriter.close();
            arabicRootsWriter.close();
            arabicLuceneWriter.close();
            englishIndexWriter.close();
            */
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}