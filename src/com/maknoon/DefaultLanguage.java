package com.maknoon;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.Properties;

class DefaultLanguage extends JDialog
{
	// Version 1.7
	ArabicIndexer.lang defaultLanguage = ArabicIndexer.lang.Arabic;

	DefaultLanguage(final ArabicIndexer MAI)
	{
		super(MAI, "اللغة المستخدمة (Default Language) (مستعمل زبان)", true);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);

		final JPanel languageChoicePanel = new JPanel();
		languageChoicePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "اختر لغة البرنامج (Choose the program language) (سوفٹ ويئر کي زبان منتخب کريں)", 0, 0, null, Color.red));
		languageChoicePanel.setPreferredSize(new Dimension(550, 60));
		add(languageChoicePanel, BorderLayout.CENTER);

		final JRadioButton arabicLanguageRadioButton = new JRadioButton("العربية", true);
		final JRadioButton englishLanguageRadioButton = new JRadioButton("English");
		final JRadioButton urduLanguageRadioButton = new JRadioButton("اردو");

		final ActionListener languageGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				if (ae.getSource() == arabicLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.Arabic;

				if (ae.getSource() == urduLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.Urdu;

				if (ae.getSource() == englishLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.English;
			}
		};
		arabicLanguageRadioButton.addActionListener(languageGroupListener);
		urduLanguageRadioButton.addActionListener(languageGroupListener);
		englishLanguageRadioButton.addActionListener(languageGroupListener);

		final ButtonGroup languageGroup = new ButtonGroup();
		languageGroup.add(arabicLanguageRadioButton);
		languageGroup.add(englishLanguageRadioButton);
		languageGroup.add(urduLanguageRadioButton);

		languageChoicePanel.add(arabicLanguageRadioButton);
		languageChoicePanel.add(new JLabel("         "));
		languageChoicePanel.add(urduLanguageRadioButton);
		languageChoicePanel.add(new JLabel("         "));
		languageChoicePanel.add(englishLanguageRadioButton);

		final JPanel closePanel = new JPanel();
		add(closePanel, BorderLayout.SOUTH);

		final JButton closeButton = new JButton("التالي (Next) (درج ذيل)");
		closeButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				ArabicIndexer.language = defaultLanguage;

				try
				{
					// Update version 2.1
					final Properties prop = new Properties();
					prop.load(new FileInputStream(ArabicIndexer.programFolder + "setting/setting.properties"));
					prop.setProperty("language", String.valueOf(ArabicIndexer.language));
					prop.store(new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "setting/setting.properties")), null);
				}
				catch (IOException ae)
				{
					ae.printStackTrace();
				}
				dispose();
			}
		});

		closePanel.add(closeButton);
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				closeButton.doClick();
			}
		});

		// To locate the JDialog at the center of the screen
		pack();
		ArabicIndexer.centerInScreen(this);

		getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		englishLanguageRadioButton.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

		setVisible(true);
	}
}