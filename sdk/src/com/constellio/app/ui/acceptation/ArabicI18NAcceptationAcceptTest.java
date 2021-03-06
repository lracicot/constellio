package com.constellio.app.ui.acceptation;

import com.constellio.data.conf.FoldersLocator;
import com.constellio.data.utils.LangUtils.ListComparisonResults;
import com.constellio.model.entities.Language;
import com.constellio.sdk.dev.tools.CompareI18nKeys;
import com.constellio.sdk.tests.ConstellioTest;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class ArabicI18NAcceptationAcceptTest extends ConstellioTest {

	private static List<String> keysWithSameFrenchEnglishValue = new ArrayList<>();

	static {
		keysWithSameFrenchEnglishValue.add("SystemConfigurationGroup.agent");
	}

	@Test
	public void ensureArabicAndFrenchLanguageFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();
		ListComparisonResults<String> results = CompareI18nKeys.compare(Language.Arabic);

		if (!results.getNewItems().isEmpty() || !results.getRemovedItems().isEmpty()) {
			String comparisonMessage = CompareI18nKeys.getComparisonMessage(Language.Arabic, results);
			fail("Missing i18n keys\n" + comparisonMessage);
		}
	}


	@Test
	public void ensureArabicAndFrenchLanguageCoreFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();

		StringBuilder stringBuilder = new StringBuilder();
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File core = new File(i18nFolder, "core");
		addComparisonMessage(core, "baseView", stringBuilder);
		addComparisonMessage(core, "imports", stringBuilder);
		addComparisonMessage(core, "managementViews", stringBuilder);
		addComparisonMessage(core, "model", stringBuilder);
		addComparisonMessage(core, "schemasManagementViews", stringBuilder);
		addComparisonMessage(core, "search", stringBuilder);
		addComparisonMessage(core, "security", stringBuilder);
		addComparisonMessage(core, "usersAndGroupsManagementViews", stringBuilder);
		addComparisonMessage(core, "userViews", stringBuilder);
		addComparisonMessage(core, "webServices", stringBuilder);

		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	@Test
	public void ensureArabicAndFrenchLanguageEsFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();

		StringBuilder stringBuilder = new StringBuilder();
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File es = new File(i18nFolder, "es");
		addComparisonMessage(es, "i18n", stringBuilder);
		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	@Test
	public void ensureArabicAndFrenchLanguageRmFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();

		StringBuilder stringBuilder = new StringBuilder();
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File rm = new File(i18nFolder, "rm");
		addComparisonMessage(rm, "audits", stringBuilder);
		addComparisonMessage(rm, "decommissioningViews", stringBuilder);
		addComparisonMessage(rm, "demo", stringBuilder);
		addComparisonMessage(rm, "foldersAndDocuments", stringBuilder);
		addComparisonMessage(rm, "managementViews", stringBuilder);
		addComparisonMessage(rm, "model", stringBuilder);
		addComparisonMessage(rm, "reports", stringBuilder);
		addComparisonMessage(rm, "storageAndContainers", stringBuilder);
		addComparisonMessage(rm, "userViews", stringBuilder);

		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	@Test
	public void ensureArabicAndFrenchLanguageRobotsFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();

		StringBuilder stringBuilder = new StringBuilder();
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File robots = new File(i18nFolder, "robots");
		addComparisonMessage(robots, "i18n", stringBuilder);

		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	@Test
	public void ensureArabicAndFrenchLanguageTasksFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();

		StringBuilder stringBuilder = new StringBuilder();
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File robots = new File(i18nFolder, "tasks");
		addComparisonMessage(robots, "model", stringBuilder);
		addComparisonMessage(robots, "views", stringBuilder);
		addComparisonMessage(robots, "workflowBeta", stringBuilder);

		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	@Test
	public void ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys()
			throws Exception {
		//		assumeArabicLabelsValidated();
		StringBuilder stringBuilder = new StringBuilder();
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("core", stringBuilder);
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("es", stringBuilder);
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("es_rm_robots", stringBuilder);
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("rm", stringBuilder);
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("robots", stringBuilder);
		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("tasks", stringBuilder);
		//		ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys("sharepointGraphAPI", stringBuilder);

		String finalComparisonMessage = stringBuilder.toString();
		if (!finalComparisonMessage.isEmpty()) {
			System.out.println(finalComparisonMessage);
			fail("Missing i18n keys");
		}
	}

	private void ensureArabicAndFrenchLanguageMigrationFilesHaveSameKeys(String module, StringBuilder stringBuilder)
			throws Exception {
		FoldersLocator foldersLocator = new FoldersLocator();
		File i18nFolder = foldersLocator.getI18nFolder();
		File[] tasksMigrationFiles = new File(new File(i18nFolder, "migrations"), module).listFiles();

		File folder = newTempFolder();
		File arabicDestinationFile = newTempFileWithContentInFolder(folder, "arabicTempFile", "");
		File frenchDestinationFile = newTempFileWithContentInFolder(folder, "frenchTempFile", "");

		for (File file : tasksMigrationFiles) {
			String keysFileName = module + "_" + file.getName();

			if (Arrays.asList(file.list()).contains(keysFileName + ".properties")) {
				String frenchFilename = keysFileName + ".properties";
				File frenchI18n = new File(file.getAbsolutePath() + "\\" + frenchFilename);
				appendFile(frenchI18n, frenchDestinationFile);
			}
			if (Arrays.asList(file.list()).contains(keysFileName + "_ar.properties")) {
				String arabicFilename = keysFileName + "_ar.properties";

				File arabicI18n = new File(file.getAbsolutePath() + "\\" + arabicFilename);
				appendFile(arabicI18n, arabicDestinationFile);
			}
		}

		addComparisonMessage(arabicDestinationFile, frenchDestinationFile, module, stringBuilder);

	}

	private void appendFile(File sourceFile, File destinationFile) throws IOException {
		BufferedReader bufferedReader = null;
		FileWriter fileWriter = null;
		try {
			bufferedReader = new BufferedReader(new FileReader(sourceFile));
			fileWriter = new FileWriter(destinationFile, true);
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				fileWriter.append(line);
				fileWriter.append("\n");
			}
		} finally {
			bufferedReader.close();
			fileWriter.close();
		}
	}

	private void addComparisonMessage(File i18nArabicFolder, File i18nFrenchFolder, String keysFileName,
									  StringBuilder stringBuilder)
			throws Exception {

		ListComparisonResults<String> results = CompareI18nKeys.compareKeys(i18nArabicFolder, i18nFrenchFolder);
		if (!results.getNewItems().isEmpty() || !results.getRemovedItems().isEmpty()) {
			stringBuilder.append(CompareI18nKeys.getComparisonMessage(Language.Arabic, results, keysFileName));
			stringBuilder.append("\n\n");
		}
	}

	private void addComparisonMessage(File i18nParentFolder, String keysFileName, StringBuilder stringBuilder)
			throws Exception {

		ListComparisonResults<String> results = CompareI18nKeys.compareKeys(Language.Arabic, i18nParentFolder, keysFileName);
		if (!results.getNewItems().isEmpty() || !results.getRemovedItems().isEmpty()) {
			stringBuilder.append(CompareI18nKeys.getComparisonMessage(Language.Arabic, results, keysFileName));
			stringBuilder.append("\n\n");
		}
	}

	protected void assumeArabicLabelsValidated() {
		assumeTrue("Arabic i18n validations are disabled, set 'skip.arabicI18nTests=false' in sdk.properties to enable them", areArabicI18nValidationsEnabled());
	}

	protected boolean areArabicI18nValidationsEnabled() {
		return "false".equalsIgnoreCase(getCurrentTestSession().getProperty("skip.arabicI18nTests"));
	}

}
