package com.maknoon;

import org.ghost4j.Ghostscript;
import org.ghost4j.GhostscriptException;

import java.io.File;
import java.util.ArrayList;

public class GS
{
	GS() throws Exception
	{
		String programFolder = new File(GS.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
		System.setProperty("jna.library.path", programFolder + "bin");

		//get Ghostscript instance
		final Ghostscript gs = Ghostscript.getInstance();

		//prepare Ghostscript interpreter parameters
		//refer to Ghostscript documentation for parameter usage
		final java.util.List<String> gsArgs = new ArrayList<>();
		gsArgs.add("-gs");
		gsArgs.add("-dNOPAUSE");
		gsArgs.add("-dQUIET");
		gsArgs.add("-dBATCH");
		gsArgs.add("-dSAFER");
		gsArgs.add("-sDEVICE=pnggray");
		gsArgs.add("-dFirstPage=10");
		gsArgs.add("-dLastPage=10");
		gsArgs.add("-r300");
		//gsArgs.add("-dGraphicsAlphaBits=4");
		//gsArgs.add("-dTextAlphaBits=4");
		gsArgs.add("-sOutputFile=F:/image_%04d.png");
		gsArgs.add("F:/1.pdf");

		//execute and exit interpreter
		try
		{
			synchronized (gs)
			{
				gs.initialize(gsArgs.toArray(new String[0]));
				gs.exit();
			}
		}
		catch (UnsatisfiedLinkError | GhostscriptException | NoClassDefFoundError e)
		{
			e.printStackTrace();
		}
		finally
		{
			//delete interpreter instance (safer)
			try
			{
				Ghostscript.deleteInstance();
			}
			catch (GhostscriptException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws Exception
	{
		new GS();
	}
}
