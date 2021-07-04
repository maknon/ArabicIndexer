package com.maknoon;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

public class OnlineConverter
{
	String ids = "(10277,10278,10279,10280,10281,10282,10283,10284,7642,8981,2759,2758)";
	String programFolder;

	OnlineConverter()
	{
		try
		{
			programFolder = new File(OnlineConverter.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
			final String eProgramFolder = programFolder.replace("\\", "\\\\");

			final String dbURL = "jdbc:h2:" + programFolder + "db/indexerDatabase";
			Class.forName("org.h2.Driver");
			final Connection sharedDBConnection = DriverManager.getConnection(dbURL);

			String listMenu = "";

			final Statement stmt = sharedDBConnection.createStatement();
			final Statement stmt1 = sharedDBConnection.createStatement();
			final Statement stmt2 = sharedDBConnection.createStatement();
			final Statement stmt3 = sharedDBConnection.createStatement();

			ResultSet rs2 = stmt2.executeQuery("SELECT author FROM arabicBook WHERE id IN " + ids + " GROUP BY author ORDER BY author");
			while (rs2.next())
			{
				final String authorName = rs2.getString("author");

				listMenu = listMenu + "\n<details>\n<summary>" + authorName + "</summary>";

				// Adding books which are just one volume.
				ResultSet rs = stmt.executeQuery("SELECT id, name, path FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '' AND id IN " + ids + ") ORDER BY name");
				boolean first = true;
				while (rs.next())
				{
					final String path = rs.getString("path");
					final String title = rs.getString("name");
					final String fileName = path.split("\\\\")[1];
					final String folderName = fileName.substring(0, fileName.length() - 4);
					final int id = rs.getInt("id");
					final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));
					cc(folderName, pdfFile, title, stmt1, id);

					if (first)
					{
						listMenu = listMenu + "\n<a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;t=" + title + "' target='_blank'>" + title + "</a>";
						first = false;
					}
					else
						listMenu = listMenu + "\n<br><a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;t=" + title + "' target='_blank'>" + title + "</a>";
				}
				rs.close();

				// Adding books which are more than one volume.
				rs = stmt.executeQuery("SELECT parent FROM arabicBook WHERE (author = '" + authorName + "' AND parent != '' AND id IN " + ids + ") GROUP BY parent");
				first = true;
				while (rs.next())
				{
					final String bookParent = rs.getString("parent");

					listMenu = listMenu + "\n<details>\n<summary>" + bookParent + "</summary>";

					final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM arabicBook WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "' AND id IN " + ids + ") ORDER BY name");
					while (rs1.next())
					{
						final String path = rs1.getString("path");
						final int id = rs1.getInt("id");
						final String title = rs1.getString("name");
						final String fileName = path.split("\\\\")[1];
						final String folderName = fileName.substring(0, fileName.length() - 4);
						final File pdfFile = new File(path.replaceFirst("root:pdf", eProgramFolder + "pdf"));

						final String htmlTitle = bookParent + " (" + title + ")";
						cc(folderName, pdfFile, htmlTitle, stmt3, id);

						if (first)
						{
							listMenu = listMenu + "\n<a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;t=" + htmlTitle + "' target='_blank'>" + title + "</a>";
							first = false;
						}
						else
							listMenu = listMenu + "\n<br><a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;t=" + htmlTitle + "' target='_blank'>" + title + "</a>";
					}
					rs1.close();
					listMenu = listMenu + "\n</details>";
				}
				rs.close();
				listMenu = listMenu + "\n</details>";
			}
			rs2.close();
			stmt.close();
			stmt1.close();
			stmt2.close();
			stmt3.close();
			sharedDBConnection.close();

			Files.writeString(new File(".", "menu.html").toPath(), listMenu);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	void cc(String folderName, File pdfFile, String title, Statement stmt, int id) throws Exception
	{
		final File newFolder = new File("F:/ai/" + folderName);
		final boolean success = newFolder.mkdirs();
		System.out.println("Folder " + newFolder + " created: " + success);

		final Process proc = Runtime.getRuntime().exec(new String[]{new File(programFolder + "bin_X/xpdf/pdftopng.exe").getAbsolutePath()/*, "-r", "30"*/, pdfFile.getAbsolutePath(), "F:/ai/" + folderName + "/t"});

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

		int maxPage = 1;
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
						final int k = Integer.parseInt(file.substring(2, file.indexOf(".")));
						fileN.renameTo(new File(fileN.getParentFile(), k + ".png"));
						if (k > maxPage) maxPage = k;
					}
					catch (NumberFormatException e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		Files.copy(pdfFile.toPath(), new File(newFolder, folderName + ".pdf").toPath(), StandardCopyOption.REPLACE_EXISTING);

		// for creating the empty pages
		final Vector<Integer> pg = new Vector<>(maxPage);
		for (int i = 1; i <= maxPage; i++)
			pg.add(i);

		final ResultSet rs = stmt.executeQuery("SELECT * FROM b" + id);
		while (rs.next())
		{
			int page = rs.getInt("page");
			pg.removeElement(page);
			String content = rs.getString("Content");
			final String html = "<!DOCTYPE html>\n" +
					"<html dir='rtl' lang='ar'>\n" +
					"<meta charset='UTF-8' name='viewport' content='width=device-width, initial-scale=1'>\n" +
					"<title>" + title + "</title>\n" +
					"<body>\n" +
					"<h3>كتاب " + title + "</h3>\n" +
					"<h4>الصفحة " + page + "</h4>\n" +
					content + "\n" +
					"<p><a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;p=" + page + "&amp;t=" + title + "'>عودة للكتاب</a>\n" +
					"&nbsp;&nbsp;<a href=\"https://www.maknoon.com/community/pages/ai/\">قائمة الكتب</a></p>\n" +
					"\n" +
					"</body>\n" +
					"</html>";

			Files.writeString(new File(newFolder, page + ".html").toPath(), html);
		}
		rs.close();

		for (int i = 0; i < pg.size(); i++)
		{
			final String html = "<!DOCTYPE html>\n" +
					"<html dir='rtl' lang='ar'>\n" +
					"<meta charset='UTF-8' name='viewport' content='width=device-width, initial-scale=1'>\n" +
					"<title>" + title + "</title>\n" +
					"<body>\n" +
					"<h3>كتاب " + title + "</h3>\n" +
					"<h4>الصفحة " + pg.elementAt(i) + "</h4>\n" +
					"الصفحة غير موجودة\n" +
					"<p><a href='https://www.maknoon.com/ai/view.php?bk=" + folderName + "&amp;p=" + pg.elementAt(i) + "&amp;t=" + title + "'>عودة للكتاب</a>\n" +
					"&nbsp;&nbsp;<a href=\"https://www.maknoon.com/community/pages/ai/\">قائمة الكتب</a></p>\n" +
					"\n" +
					"</body>\n" +
					"</html>";

			Files.writeString(new File(newFolder, pg.elementAt(i) + ".html").toPath(), html);
		}
	}

	public static void main(String[] args)
	{
		new OnlineConverter();
	}
}