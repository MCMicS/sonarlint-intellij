/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.ui.scope.CurrentFileScope;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.TreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintIssuesPanel extends SimpleToolWindowPanel implements OccurenceNavigator, DataProvider {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.issuestoolwindow";
  private static final String SPLIT_PROPORTION = "SONARLINT_ISSUES_SPLIT_PROPORTION";

  private final Project project;
  private final IssueManager issueManager;
  private Tree tree;
  private ActionToolbar mainToolbar;
  private AbstractScope scope;
  private TreeModelBuilder treeBuilder;
  private SonarLintRulePanel rulePanel;

  public SonarLintIssuesPanel(Project project) {
    super(false, true);
    this.project = project;
    this.issueManager = project.getComponent(IssueManager.class);
    this.scope = new CurrentFileScope(project);

    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);

    addToolbar();

    JPanel issuesPanel = new JPanel(new BorderLayout());
    createTree();
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

    rulePanel = new SonarLintRulePanel(project, projectBindingManager);

    JScrollPane scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.getPanel(),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);

    super.setContent(createSplitter(issuesPanel, scrollableRulePanel));

    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, new IssueStoreListener() {

      @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
        ApplicationManager.getApplication().invokeLater(() -> {
          treeBuilder.updateFiles(map);
          expandTree();
        });
      }

      @Override public void allChanged() {
        ApplicationManager.getApplication().invokeLater(SonarLintIssuesPanel.this::updateTree);
      }
    });

    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus -> ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
    updateTree();
  }

  private JComponent createSplitter(JComponent c1, JComponent c2) {
    float savedProportion = PropertiesComponent.getInstance(project).getFloat(SPLIT_PROPORTION, 0.65f);

    final Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(c1);
    splitter.setSecondComponent(c2);
    splitter.setProportion(savedProportion);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION,
      evt -> PropertiesComponent.getInstance(project).setValue(SPLIT_PROPORTION, Float.toString(splitter.getProportion())));

    return splitter;
  }

  private void addToolbar() {
    ActionGroup mainActionGroup = (ActionGroup) ActionManager.getInstance().getAction(GROUP_ID);
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, mainActionGroup, false);
    mainToolbar.setTargetComponent(this);
    Box toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  public void updateTree() {
    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile = new HashMap<>();
    Collection<VirtualFile> all = scope.getAll();
    for (VirtualFile f : all) {
      issuesPerFile.put(f, issueManager.getForFile(f));
    }

    treeBuilder.updateModel(issuesPerFile, scope.getCondition());
    expandTree();
  }

  private void expandTree() {
    TreeUtil.expandAll(tree);
  }

  private void issueTreeSelectionChanged() {
    IssueNode[] selectedNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      rulePanel.setRuleKey(selectedNodes[0].issue());
    } else {
      rulePanel.setRuleKey(null);
    }
  }

  private void createTree() {
    treeBuilder = new TreeModelBuilder();
    DefaultTreeModel model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    tree.addTreeSelectionListener(e -> issueTreeSelectionChanged());
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (AbstractScope.SCOPE_DATA_KEY.is(dataId)) {
      return scope;
    }

    return null;
  }

  @CheckForNull
  private OccurenceInfo occurrence(@Nullable IssueNode node) {
    if (node == null) {
      return null;
    }

    TreePath path = new TreePath(node.getPath());
    tree.getSelectionModel().setSelectionPath(path);
    tree.scrollPathToVisible(path);

    RangeMarker range = node.issue().getRange();
    int startOffset = (range != null) ? range.getStartOffset() : 0;
    return new OccurenceInfo(
      new OpenFileDescriptor(project, node.issue().psiFile().getVirtualFile(), startOffset),
      -1,
      -1);
  }

  @Override public boolean hasNextOccurence() {
    // relies on the assumption that a TreeNodes will always be the last row in the table view of the tree
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node instanceof IssueNode) {
      return tree.getRowCount() != tree.getRowForPath(path) + 1;
    } else {
      return node.getChildCount() > 0;
    }
  }

  @Override public boolean hasPreviousOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    return (node instanceof IssueNode) && !isFirst(node);
  }

  private static boolean isFirst(final TreeNode node) {
    final TreeNode parent = node.getParent();
    return parent == null || (parent.getIndex(node) == 0 && isFirst(parent));
  }

  @CheckForNull
  @Override
  public OccurenceInfo goNextOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getNextIssue((AbstractNode<?>) path.getLastPathComponent()));
  }

  @CheckForNull
  @Override
  public OccurenceInfo goPreviousOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getPreviousIssue((AbstractNode<?>) path.getLastPathComponent()));
  }

  @Override public String getNextOccurenceActionName() {
    return "Next Issue";
  }

  @Override public String getPreviousOccurenceActionName() {
    return "Previous Issue";
  }
}
