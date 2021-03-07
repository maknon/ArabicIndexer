package com.maknoon;

import java.io.File;
import java.sql.*;

// Version 1.4, H2 instead of Derby
// Version 1.7, Both H2/Derby
public final class CreateIndexerDatabase
{
	static private boolean create = true; // true -> create new database, false -> only empty the database

	public static void main(String[] args)
	{
		if (args.length != 0) create = !args[0].equals("false");
		new CreateIndexerDatabase();
	}

	private CreateIndexerDatabase()
	{
		try
		{
			final String programFolder = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + "/";

			// Version 1.7
			Connection conn;
			if (ArabicIndexer.derbyInUse)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
				conn = DriverManager.getConnection("jdbc:derby:" + programFolder + "db" + (create ? ";create=true" : ""));
			}
			else
			{
				Class.forName("org.h2.Driver");
				conn = DriverManager.getConnection("jdbc:h2:" + programFolder + "db/indexerDatabase");
			}

			final Statement s = conn.createStatement();

			if (!create)
			{
				final ResultSet rs = s.executeQuery("SELECT id FROM arabicBook UNION ALL SELECT id FROM englishBook");
				while (rs.next())
					s.execute("DROP TABLE b" + rs.getString("id"));

				s.execute("DROP TABLE arabicBook");
				s.execute("DROP TABLE englishBook");

                /* Version 1.7, Deprecated as of Lucene 3.5.0
                // Clear the index
                IndexWriter.unlock(FSDirectory.open(new File("arabicIndex")));
                IndexWriter.unlock(FSDirectory.open(new File("arabicLuceneIndex")));
                IndexWriter.unlock(FSDirectory.open(new File("arabicRootsIndex")));
                IndexWriter.unlock(FSDirectory.open(new File("englishIndex")));
                
                final IndexWriter arabicIndexWriter = new IndexWriter(FSDirectory.open(new File("arabicIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
                final IndexWriter arabicRootsWriter = new IndexWriter(FSDirectory.open(new File("arabicRootsIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
                final IndexWriter arabicLuceneWriter = new IndexWriter(FSDirectory.open(new File("arabicLuceneIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
                final IndexWriter englishIndexWriter = new IndexWriter(FSDirectory.open(new File("englishIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

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

			// path column should be the last column because this will affect the export process
			// Version 1.5, isImagedBook is removed
			// Version 1.6, Add 'author'
			// Version 2.1, Add 'id' so that book table is named with this id. the main reason is to allow having multiple books with the same Path. this will save huge space since there are many duplicates in الوقفية version
			s.execute("CREATE TABLE arabicBook(id BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1), name VARCHAR(250), parent VARCHAR(250), category VARCHAR(250), author VARCHAR(250), path VARCHAR(260), PRIMARY KEY (ID))"); // Version 1.6, Windows Path is 260 max
			s.execute("CREATE UNIQUE INDEX arabicBookPath ON arabicBook(name, parent, category, author, path)");

			// Version 1.9, create index
			s.execute("CREATE INDEX arabicBookCategory ON arabicBook(category)");
			s.execute("CREATE INDEX arabicBookParent ON arabicBook(parent)");
			s.execute("CREATE INDEX arabicBookAuthor ON arabicBook(author)");
			s.execute("CREATE INDEX arabicBookId ON arabicBook(id)");

			s.execute("CREATE TABLE englishBook(id BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1000000), name VARCHAR(250), parent VARCHAR(250), category VARCHAR(250), author VARCHAR(250), path VARCHAR(260), PRIMARY KEY (ID))");
			s.execute("CREATE UNIQUE INDEX englishBookPath ON englishBook(name, parent, category, author, path)");

			// Version 1.9, create index
			s.execute("CREATE INDEX englishBookCategory ON englishBook(category)");
			s.execute("CREATE INDEX englishBookParent ON englishBook(parent)");
			s.execute("CREATE INDEX englishBookAuthor ON englishBook(author)");
			s.execute("CREATE INDEX englishBookId ON englishBook(id)");
			conn.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}