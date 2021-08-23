package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OnlineConverter
{
	final String base = "C:/Users/Ebrahim/Desktop/ai/bk/";

	final static boolean allDB = false;
	String ids = "(6305,6304)";
	String programFolder;
	Connection sharedDBConnection;

	OnlineConverter() throws Exception
	{
		final ExecutorService service = Executors.newFixedThreadPool(10);

		programFolder = new File(OnlineConverter.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
		final String eProgramFolder = programFolder.replace("\\", "\\\\");

		final String dbURL = "jdbc:h2:" + programFolder + "db/indexerDatabase";
		Class.forName("org.h2.Driver");
		sharedDBConnection = DriverManager.getConnection(dbURL);

		final Vector<String> pdfFiles = new Vector<>();

		String listMenu = "";

		final Statement stmt2 = sharedDBConnection.createStatement();
		final ResultSet rs2;
		if (allDB)
			rs2 = stmt2.executeQuery("SELECT author FROM arabicBook GROUP BY author ORDER BY author");
		else
			rs2 = stmt2.executeQuery("SELECT author FROM arabicBook WHERE id IN " + ids + " GROUP BY author ORDER BY author");
		while (rs2.next())
		{
			final String authorName = rs2.getString("author");

			listMenu = listMenu + "\n<details>\n<summary>" + authorName + "</summary>";

			// Adding books which are just one volume.
			final Statement stmt = sharedDBConnection.createStatement();
			ResultSet rs;
			if (allDB)
				rs = stmt.executeQuery("SELECT id, name, path FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '') ORDER BY name");
			else
				rs = stmt.executeQuery("SELECT id, name, path FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '' AND id IN " + ids + ") ORDER BY name");
			boolean first = true;
			while (rs.next())
			{
				final String path = rs.getString("path");
				final String title = rs.getString("name");
				final String fileName = path.split("\\\\")[1];
				final String folderName = fileName.substring(0, fileName.length() - 4);
				final int id = rs.getInt("id");
				final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));

				if (pdfFiles.contains(pdfFile.toString()))
					service.submit(new ccT(folderName, pdfFile, title, id, true, null));
				else
				{
					pdfFiles.add(pdfFile.toString());
					service.submit(new ccT(folderName, pdfFile, title, id, false, null));
				}

				int firstPage = 1;
				final Statement stmt3 = sharedDBConnection.createStatement();
				final ResultSet rs3 = stmt3.executeQuery("SELECT * FROM b" + id + " WHERE page=(SELECT MIN(page) FROM b" + id + ")");
				if (rs3.next())
					firstPage = rs3.getInt("page");
				rs3.close();
				stmt3.close();

				if (first)
				{
					listMenu = listMenu + "\n<a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + firstPage + "&amp;t=" + title + "' target='_blank'>" + title + "</a>";
					first = false;
				}
				else
					listMenu = listMenu + "\n<br><a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + firstPage + "&amp;t=" + title + "' target='_blank'>" + title + "</a>";
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

				listMenu = listMenu + "\n<details>\n<summary>" + bookParent + "</summary>";

				final Statement stmt1 = sharedDBConnection.createStatement();
				final ResultSet rs1;
				if (allDB)
					rs1 = stmt1.executeQuery("SELECT * FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "') ORDER BY LENGTH(name), name");
				else
					rs1 = stmt1.executeQuery("SELECT * FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "' AND id IN " + ids + ") ORDER BY LENGTH(name), name");
				first = true;
				while (rs1.next())
				{
					final String path = rs1.getString("path");
					final int id = rs1.getInt("id");
					final String title = rs1.getString("name");
					final String fileName = path.split("\\\\")[1];
					final String folderName = fileName.substring(0, fileName.length() - 4);
					final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));

					final String htmlTitle = bookParent + " (" + title + ")";

					if (pdfFiles.contains(pdfFile.toString()))
						service.submit(new ccT(folderName, pdfFile, bookParent, id, true, title));
					else
					{
						pdfFiles.add(pdfFile.toString());
						service.submit(new ccT(folderName, pdfFile, bookParent, id, false, title));
					}

					int firstPage = 1;
					final Statement stmt3 = sharedDBConnection.createStatement();
					final ResultSet rs3 = stmt3.executeQuery("SELECT * FROM b" + id + " WHERE page=(SELECT MIN(page) FROM b" + id + ")");
					if (rs3.next())
						firstPage = rs3.getInt("page");
					rs3.close();
					stmt3.close();

					if (first)
					{
						listMenu = listMenu + "\n<a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + firstPage + "&amp;t=" + htmlTitle + "' target='_blank'>" + title + "</a>";
						first = false;
					}
					else
						listMenu = listMenu + "\n<br><a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + firstPage + "&amp;t=" + htmlTitle + "' target='_blank'>" + title + "</a>";
				}
				rs1.close();
				listMenu = listMenu + "\n</details>";
			}
			rs.close();
			stmt.close();
			listMenu = listMenu + "\n</details>";
		}
		rs2.close();
		stmt2.close();

		Files.writeString(new File(".", "menu.html").toPath(), listMenu);

		service.shutdown();
		service.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

		sharedDBConnection.close();
	}

	void cc(String folderName, File pdfFile, String bookTitle, int id, boolean repeatedPdf, String chapter)
	{
		try
		{
			String title;
			if (chapter == null)
				title = bookTitle;
			else
				title = bookTitle + " (" + chapter + ")";

			final File newFolder = new File(base + folderName);
			int maxPage = 1;

			if (!repeatedPdf)
			{
				final boolean success = newFolder.mkdirs();
				System.out.println("Folder " + newFolder + " created: " + success);

				final Process proc = Runtime.getRuntime().exec(new String[]{new File(programFolder + "bin_X/xpdf/pdftopng.exe").getAbsolutePath()/*, "-r", "30"*/, pdfFile.getAbsolutePath(), base + folderName + "/t"});

				// Capture the Error/Output streams from the SumatraPDF process
				class StreamGrabber extends Thread
				{
					final InputStream is;
					final String type;

					StreamGrabber(InputStream is, String type)
					{
						this.is = is;
						this.type = type;
					}

					public void run()
					{
						try
						{
							final BufferedReader br = new BufferedReader(new InputStreamReader(is));
							String line;
							while ((line = br.readLine()) != null)
								System.out.println(type + ">" + line);
							br.close();
							is.close();
						}
						catch (IOException ioe)
						{
							ioe.printStackTrace();
						}
					}
				}

				final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "ERROR"); // any error message?
				final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "OUTPUT"); // any output?

				errorGrabber.start();
				outputGrabber.start();

				// any error???
				final int exitVal = proc.waitFor();
				System.out.println("ExitValue: " + exitVal);

				if (exitVal == 1)
				{
					// faster and overcome any errors but generate bigger png files compared to xpdf tools
					final Process proc1 = Runtime.getRuntime().exec(new String[]{new File(programFolder + "bin_X/mutool.exe").getAbsolutePath(), "convert", "-O", "width=1200", "-o", base + folderName + "/%d.png", pdfFile.getAbsolutePath()});

					final StreamGrabber errorGrabber1 = new StreamGrabber(proc1.getErrorStream(), "ERROR"); // any error message?
					final StreamGrabber outputGrabber1 = new StreamGrabber(proc1.getInputStream(), "OUTPUT"); // any output?

					errorGrabber1.start();
					outputGrabber1.start();

					// any error???
					final int exitVal1 = proc1.waitFor();
					System.out.println("New ExitValue: " + exitVal1);
				}

				final File[] filesInDir = newFolder.listFiles();
				if (filesInDir != null)
				{
					for (final File fileN : filesInDir)
					{
						final String file = fileN.getName();
						if (file.endsWith(".png"))
						{
							try
							{
								if (exitVal == 0)
								{
									final int k = Integer.parseInt(file.substring(2, file.indexOf(".")));
									fileN.renameTo(new File(fileN.getParentFile(), k + ".png"));
									if (k > maxPage)
										maxPage = k;
								}
								else
								{
									final int k = Integer.parseInt(file.substring(0, file.indexOf(".")));
									if (k > maxPage)
										maxPage = k;
								}
							}
							catch (NumberFormatException e)
							{
								e.printStackTrace();
							}
						}
					}
				}

				Files.copy(pdfFile.toPath(), new File(newFolder, folderName + ".pdf").toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			// for creating the empty pages
			final Vector<Integer> pg = new Vector<>();
			for (int i = 1; i <= maxPage; i++)
				pg.add(i);

			final Statement stmt = sharedDBConnection.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT * FROM b" + id);
			while (rs.next())
			{
				final int page = rs.getInt("page");
				pg.removeElement(page);

				final File f = new File(newFolder, page + ".html");
				final File p = new File(newFolder, page + ".png");

				if(!f.exists() && p.exists())
				{
					final String content = rs.getString("Content");
					/*
					final String html = "<!DOCTYPE html>\n" +
							"<html dir='rtl' lang='ar'>\n" +
							"<meta charset='UTF-8' name='viewport' content='width=device-width, initial-scale=1'>\n" +
							"<title>" + title + "</title>\n" +
							"<body>\n" +
							"<h3>كتاب " + title + "</h3>\n" +
							"<h4>الصفحة " + page + "</h4>\n" +
							content.replace("\n", "<br>") + "\n" +
							"<p><a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + page + "&amp;t=" + title + "'>عودة للكتاب</a>\n" +
							"&nbsp;&nbsp;<a href=\"https://maknoon.com/community/pages/ai/\">قائمة الكتب</a></p>\n" +
							"\n" +
							"</body>\n" +
							"</html>";
					*/

					final String html = "<h4>كتاب " + title + "</h4>\n" +
							content.replace("\n", "<br>") + "\n" +
							"<h4>الصفحة " + page + "</h4>";

					try
					{
						Files.writeString(f.toPath(), html, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); // do not overwrite in case of multiple books directed to the same pdf file. the first one is enough
					}
					catch (FileAlreadyExistsException e)
					{
						// Should be thrown! because we checked before for file.exists()
						e.printStackTrace();
					}
				}
			}
			rs.close();
			stmt.close();

			/*
			if (!repeatedPdf)
			{
				for (int i = 0; i < pg.size(); i++)
				{
					final String html = "<!DOCTYPE html>\n" +
							"<html dir='rtl' lang='ar'>\n" +
							"<meta charset='UTF-8' name='viewport' content='width=device-width, initial-scale=1'>\n" +
							"<title>" + bookTitle + "</title>\n" +
							"<body>\n" +
							"<h3>كتاب " + bookTitle + "</h3>\n" +
							"<h4>الصفحة " + pg.elementAt(i) + "</h4>\n" +
							"الصفحة غير موجودة\n" +
							"<p><a href='https://maknoon.org/ai/view.php?bk=" + folderName + "&amp;p=" + pg.elementAt(i) + "&amp;t=" + bookTitle + "'>عودة للكتاب</a>\n" +
							"&nbsp;&nbsp;<a href=\"https://maknoon.com/community/pages/ai/\">قائمة الكتب</a></p>\n" +
							"\n" +
							"</body>\n" +
							"</html>";

					try
					{
						Files.writeString(new File(newFolder, pg.elementAt(i) + ".html").toPath(), html, StandardOpenOption.CREATE_NEW); // DO NOT OVERWRITE. in many cases, because of parallel processing, creating the first book empty files came after creating the other books and this overwrite the original pages !
					}
					catch (FileAlreadyExistsException e)
					{
						//e.printStackTrace();
					}
				}
			}
			*/
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private class ccT implements Runnable
	{
		String folderName;
		File pdfFile;
		String bookTitle;
		int id;
		boolean repeatedPdf;
		String chapter;

		ccT(String folderName, File pdfFile, String bookTitle, int id, boolean repeatedPdf, String chapter)
		{
			this.folderName = folderName;
			this.pdfFile = pdfFile;
			this.bookTitle = bookTitle;
			this.id = id;
			this.repeatedPdf = repeatedPdf;
			this.chapter = chapter;
		}

		@Override
		public void run()
		{
			cc(folderName, pdfFile, bookTitle, id, repeatedPdf, chapter);
		}
	}

	public static void main(String[] args) throws Exception
	{
		new OnlineConverter();
	}
}