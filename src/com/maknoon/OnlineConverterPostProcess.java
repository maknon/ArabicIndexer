package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

// This class is needed to be run after OnlineConverter
// The main reason is that parallel processing causes second volumes that uses the same PDF file does not generate html files
// This is due to that png files are generated at the same time the other volume is checking if png file is generated `if(!f.exists() && p.exists())` will fail since png files are not ready yet
// Solution is to run it again after generating all png files
public class OnlineConverterPostProcess
{
	final String inBase = "F:/ai-online/bk/";
	final String outBase = "C:/Users/Ebrahim/Desktop/ai/bk/";

	final static boolean allDB = true;
	String ids = "(6305,6304)";
	String programFolder;
	Connection sharedDBConnection;

	OnlineConverterPostProcess() throws Exception
	{
		programFolder = new File(OnlineConverterPostProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
		final String eProgramFolder = programFolder.replace("\\", "\\\\");

		final String dbURL = "jdbc:h2:" + programFolder + "db/indexerDatabase";
		Class.forName("org.h2.Driver");
		sharedDBConnection = DriverManager.getConnection(dbURL);

		final Vector<String> pdfFiles = new Vector<>();

		final Statement stmt2 = sharedDBConnection.createStatement();
		final Statement stmt = sharedDBConnection.createStatement();
		final Statement stmt1 = sharedDBConnection.createStatement();
		final Statement stmt4 = sharedDBConnection.createStatement();
		final Statement stmt5 = sharedDBConnection.createStatement();

		final ResultSet rs2;
		if (allDB)
			rs2 = stmt2.executeQuery("SELECT author FROM arabicBook GROUP BY author ORDER BY author");
		else
			rs2 = stmt2.executeQuery("SELECT author FROM arabicBook WHERE id IN " + ids + " GROUP BY author ORDER BY author");
		while (rs2.next())
		{
			final String authorName = rs2.getString("author");

			// Adding books which are just one volume.
			ResultSet rs;
			if (allDB)
				rs = stmt.executeQuery("SELECT id, name, path FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '') ORDER BY name");
			else
				rs = stmt.executeQuery("SELECT id, name, path FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '' AND id IN " + ids + ") ORDER BY name");
			while (rs.next())
			{
				final String path = rs.getString("path");
				final String title = rs.getString("name");
				final String fileName = path.split("\\\\")[1];
				final String folderName = fileName.substring(0, fileName.length() - 4);
				final int id = rs.getInt("id");
				final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));

				if (pdfFiles.contains(pdfFile.toString()))
					cc(folderName, pdfFile, title, id, true, null, stmt5);
				else
				{
					pdfFiles.add(pdfFile.toString());
					cc(folderName, pdfFile, title, id, false, null, stmt5);
				}
			}
			rs.close();

			// Adding books which are more than one volume.
			if (allDB)
				rs = stmt.executeQuery("SELECT parent FROM arabicBook WHERE (author = '" + authorName + "' AND parent != '') GROUP BY parent");
			else
				rs = stmt.executeQuery("SELECT parent FROM arabicBook WHERE (author = '" + authorName + "' AND parent != '' AND id IN " + ids + ") GROUP BY parent");
			while (rs.next())
			{
				final String bookParent = rs.getString("parent");

				final ResultSet rs1;
				if (allDB)
					rs1 = stmt1.executeQuery("SELECT * FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "') ORDER BY LENGTH(name), name");
				else
					rs1 = stmt1.executeQuery("SELECT * FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "' AND id IN " + ids + ") ORDER BY LENGTH(name), name");
				while (rs1.next())
				{
					final String path = rs1.getString("path");
					final int id = rs1.getInt("id");
					final String title = rs1.getString("name");
					final String fileName = path.split("\\\\")[1];
					final String folderName = fileName.substring(0, fileName.length() - 4);
					final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));

					if (pdfFiles.contains(pdfFile.toString()))
						cc(folderName, pdfFile, bookParent, id, true, title, stmt5);
					else
					{
						pdfFiles.add(pdfFile.toString());
						cc(folderName, pdfFile, bookParent, id, false, title, stmt5);
					}
				}
				rs1.close();
			}
			rs.close();
		}
		rs2.close();
		stmt2.close();
		stmt.close();
		stmt4.close();
		stmt5.close();

		sharedDBConnection.close();
	}

	void cc(String folderName, File pdfFile, String bookTitle, int id, boolean repeatedPdf, String chapter, Statement stmt)
	{
		try
		{
			String title;
			if (chapter == null)
				title = bookTitle;
			else
				title = bookTitle + " (" + chapter + ")";

			final File newFolder1 = new File(inBase + folderName);
			final File newFolder2 = new File(outBase + folderName);

			//if(newFolder1.exists() && newFolder1.isDirectory() && isEmpty(newFolder1))
			{
				final boolean success = newFolder2.mkdirs();
				System.out.println("Folder " + newFolder2 + " created: " + success);

				final ResultSet rs = stmt.executeQuery("SELECT * FROM b" + id);
				while (rs.next())
				{
					final int page = rs.getInt("page");

					final File f2 = new File(newFolder2, page + ".html");
					final File f = new File(newFolder1, page + ".html");
					final File p = new File(newFolder1, page + ".png");

					if (!f.exists() && p.exists())
					{
						final String content = rs.getString("Content");

						final String html = "<h4>كتاب " + title + "</h4>\n" +
								content.replace("\n", "<br>") + "\n" +
								"<h4>الصفحة " + page + "</h4>";

						try
						{
							Files.writeString(f2.toPath(), html, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); // do not overwrite in case of multiple books directed to the same pdf file. the first one is enough
						}
						catch (FileAlreadyExistsException e)
						{
							// Should be thrown! because we checked before for file.exists()
							e.printStackTrace();
						}
					}
				}
				rs.close();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public boolean isEmpty(File directory) throws IOException
	{
		DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath());
		return !stream.iterator().hasNext();
	}

	public static void main(String[] args) throws Exception
	{
		new OnlineConverterPostProcess();
	}
}