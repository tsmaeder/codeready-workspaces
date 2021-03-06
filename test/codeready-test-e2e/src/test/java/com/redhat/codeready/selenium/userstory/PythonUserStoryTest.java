/*
* Copyright (c) 2019 Red Hat, Inc.

* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-2.0
*
* Contributors:
*   Red Hat, Inc. - initial API and implementation
*/
package com.redhat.codeready.selenium.userstory;

import static com.redhat.codeready.selenium.pageobject.dashboard.CodereadyNewWorkspace.CodereadyStacks.PYTHON;
import static java.util.Arrays.asList;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Assistant.ASSISTANT;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Assistant.FIND_REFERENCES;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Assistant.GO_TO_SYMBOL;
import static org.eclipse.che.selenium.core.constant.TestProjectExplorerContextMenuConstants.ContextMenuCommandGoals.RUN_GOAL;
import static org.eclipse.che.selenium.core.constant.TestTimeoutsConstants.ELEMENT_TIMEOUT_SEC;
import static org.eclipse.che.selenium.pageobject.CodenvyEditor.MarkerLocator.WARNING;
import static org.openqa.selenium.Keys.ARROW_LEFT;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.redhat.codeready.selenium.pageobject.CodereadyEditor;
import com.redhat.codeready.selenium.pageobject.dashboard.CodereadyNewWorkspace;
import java.util.List;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.pageobject.AssistantFindPanel;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.FindReferencesConsoleTab;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.testng.annotations.Test;

/** @author Skoryk Serhii */
public class PythonUserStoryTest extends AbstractUserStoryTest {
  private static final String PROJECT_NAME = "console-python3-simple";
  private static final String PYTHON_FILE_NAME = "main.py";
  private static final String PATH_TO_FILE = PROJECT_NAME + "/" + PYTHON_FILE_NAME;
  private static final String LS_INIT_MESSAGE =
      "Initialized language server 'org.eclipse.che.plugin.python.languageserver";
  private static final String EXPECTED_HOVER_TEXT = "towers(i, start, finish, middle)";
  private static final String EXPECTED_FIND_REFERENCE_NODE_TEXT =
      "/console-python3-simple/main.py\n" + "From:10:9 To:10:16";
  private static final List<String> EXPECTED_GO_TO_SYMBOL_NODES =
      asList("countersymbols (3)", "towers", "counter");
  private static final String TEXT_FOR_INVOKING_SIGNATURE_HELP = "towers(";
  private static final String EXPECTED_SIGNATURE_TEXT = "towers(i, start, finish, middle)";
  private static final String EXPECTED_LINE_TEXT = "counter = 0";
  private static final String FILE_CONTENT =
      "counter = 0\n"
          + "\n"
          + "\n"
          + "def towers(i, start, finish, middle):\n"
          + "    global counter\n"
          + "    if i > 0:\n"
          + "        towers(i-1, start, middle, finish)\n"
          + "        print('move disk from ', start, ' to ', finish)\n"
          + "        towers(i-1, middle, finish, start)\n"
          + "        counter = counter + 1\n"
          + "\n"
          + "\n"
          + "towers(5, 'X', 'Z', 'Y')\n"
          + "print(\"\\nPuzzle solved in \" + str(counter) + \" steps.\")";

  @Inject private Menu menu;
  @Inject private Consoles consoles;
  @Inject private CodereadyEditor editor;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private AssistantFindPanel assistantFindPanel;
  @Inject private TestProjectServiceClient testProjectServiceClient;
  @Inject private FindReferencesConsoleTab findReferencesConsoleTab;

  @Override
  protected CodereadyNewWorkspace.CodereadyStacks getStackName() {
    return PYTHON;
  }

  @Override
  protected List<String> getProjects() {
    return ImmutableList.of(PROJECT_NAME);
  }

  @Override
  @Test
  public void createWorkspaceFromDashboard() throws Exception {
    super.createWorkspaceFromDashboard();

    consoles.executeCommandFromProjectExplorer(PROJECT_NAME, RUN_GOAL, "run", "Hello, world!");
    consoles.executeCommandFromProjectExplorer(
        PROJECT_NAME, RUN_GOAL, "console-python3-simple:run", "Hello, world!");

    checkLanguageServerInitialization(PROJECT_NAME, PYTHON_FILE_NAME, LS_INIT_MESSAGE);

    testProjectServiceClient.updateFile(testWorkspace.getId(), PATH_TO_FILE, FILE_CONTENT);
    editor.waitTextIntoEditor("def towers(i, start, finish, middle):");
    consoles.executeCommandFromProjectExplorer(
        PROJECT_NAME, RUN_GOAL, "console-python3-simple:run", "Puzzle solved in 31 steps.");
  }

  @Test(priority = 1)
  public void checkBasicPythonLanguageServerFeatures() {
    // check Hover feature
    editor.moveCursorToText("towers");
    editor.waitTextInHoverPopUpEqualsTo(EXPECTED_HOVER_TEXT);

    // check Find Reference feature
    editor.goToCursorPositionVisible(10, 14);
    menu.runCommand(ASSISTANT, FIND_REFERENCES);
    findReferencesConsoleTab.waitAllReferencesWithText(EXPECTED_FIND_REFERENCE_NODE_TEXT);
    findReferencesConsoleTab.doubleClickOnReference("From:10:9 To:10:16");
    editor.waitSpecifiedValueForLineAndChar(10, 16);
    editor.typeTextIntoEditor(ARROW_LEFT.toString());
    editor.waitSpecifiedValueForLineAndChar(10, 9);
    editor.waitTextElementsActiveLine("counter =");

    // check Go To Symbol feature
    menu.runCommand(ASSISTANT, GO_TO_SYMBOL);
    assistantFindPanel.waitAllNodes(EXPECTED_GO_TO_SYMBOL_NODES);
    assistantFindPanel.clickOnActionNodeWithTextContains(EXPECTED_GO_TO_SYMBOL_NODES.get(0));
    editor.waitCursorPosition(1, 1);
    editor.waitVisibleTextEqualsTo(1, EXPECTED_LINE_TEXT);

    // check Signature Help feature
    editor.setCursorToLine(12);
    editor.typeTextIntoEditor(TEXT_FOR_INVOKING_SIGNATURE_HELP);
    editor.waitSignaturesContainer();
    editor.waitProposalIntoSignaturesContainer(EXPECTED_SIGNATURE_TEXT);
    editor.closeSignaturesContainer();
    editor.waitSignaturesContainerIsClosed();

    // check warning marker
    editor.waitMarkerInPosition(WARNING, 12);
    editor.deleteCurrentLineAndInsertNew();
    editor.waitAllMarkersInvisibility(WARNING);
  }

  private void checkLanguageServerInitialization(
      String projectName, String fileName, String textInTerminal) {
    projectExplorer.waitAndSelectItem(projectName);
    projectExplorer.openItemByPath(projectName);
    projectExplorer.openItemByPath(projectName + "/" + fileName);
    editor.waitTabIsPresent(fileName);

    // check a language server initialized
    consoles.selectProcessByTabName("dev-machine");
    consoles.waitExpectedTextIntoConsole(textInTerminal, ELEMENT_TIMEOUT_SEC);
  }
}
