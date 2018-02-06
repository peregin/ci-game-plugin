package hudson.plugins.cigame.util;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;

import java.lang.reflect.Method;
import java.util.*;

public class ChangeLogRetriever {

    /**
     * Extracts the change logs from a generic representation of a job, workflow or pipeline.
     * In most of the cases the <code>run</code> is an instance of <code>AbstractBuild</code>.
     * In case of a workflow where the job is defined in a pipeline the <code>run</code> is
     * an instance of <code>org.jenkinsci.plugins.workflow.job.WorkflowRun</code>.
     * In this case the CI Game plugin can be triggered as a build step in the post actions:
     * <code>
     *     post {
     *       always {
     *         ciGame()
     *       }
     *     }
     * </code>
     * as the plugin implements the <code>SimpleBuildStep</code> interface as well.
     *
     * @param run is particular instance of a job.
     * @return the set of changes
     */
    public static List<User> getChangeLogUsers(final Run<?, ?> run) {
        if (run instanceof AbstractBuild<?, ?>) {
            ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet = ((AbstractBuild<?, ?>) run).getChangeSet();
            if (changeLogSet == null) {

            }
            return changeLogSet == null ? new ArrayList<User>() : map(changeLogSet);
        } else {
            List<User> users = new ArrayList<User>();
            try {
                // see http://javadoc.jenkins.io/plugin/workflow-job/org/jenkinsci/plugins/workflow/job/WorkflowRun.html
                final Method method = run.getClass().getMethod("getChangeSets");
                List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeLogSets = (List<ChangeLogSet<? extends ChangeLogSet.Entry>>) method.invoke(run);
                if (changeLogSets != null) {
                    for (ChangeLogSet<? extends ChangeLogSet.Entry> set : changeLogSets) {
                        users.addAll(map(set));
                    }
                }
            } catch (Exception any) {
                any.printStackTrace();
            }
            return users;
        }
    }

    private static List<User> map(final ChangeLogSet<? extends ChangeLogSet.Entry> changeLog) {
        final List<User> users = new ArrayList<User>();
        for (ChangeLogSet.Entry entry: changeLog) {
            users.add(entry.getAuthor());
        }
        return users;
    }
}
