package hudson.plugins.cigame;

import java.io.IOException;
import java.util.*;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.cigame.model.RuleBook;
import hudson.plugins.cigame.model.ScoreCard;
import hudson.plugins.cigame.util.ChangeLogRetriever;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class GamePublisher extends Notifier implements SimpleBuildStep {

    @DataBoundConstructor
    public GamePublisher() {
    }

    @Override
    public GameDescriptor getDescriptor() {
        return (GameDescriptor) super.getDescriptor();
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    /**
     * Runs a build step that is called after the build is completed.
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        perform(build, getDescriptor().getRuleBook(), getDescriptor().getNamesAreCaseSensitive(), listener);
        return true;
    }

    /**
     * Runs a build step which may be called at an arbitrary time during a build.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        perform(build, getDescriptor().getRuleBook(), getDescriptor().getNamesAreCaseSensitive(), taskListener);
    }

    /**
     * Calculates score from the build and rule book and adds a Game action to the build.
     * @param build build to calculate points for
     * @param ruleBook rules used in calculation
     * @param usernameIsCasesensitive user names in Hudson are case insensitive.
     * @param listener the build listener
     * @return true, if any user scores were updated; false, otherwise
     * @throws IOException thrown if there was a problem setting a user property
     */
    boolean perform(Run<?, ?> build, RuleBook ruleBook, boolean usernameIsCasesensitive, TaskListener listener) throws IOException {
        ScoreCard sc = new ScoreCard();
        sc.record(build, ruleBook, listener);

        ScoreCardAction action = new ScoreCardAction(sc, build);
        build.getActions().add(action);
        
        List<Run<?, ?>> accountableBuilds = new ArrayList<Run<?,?>>();
        accountableBuilds.add(build);

        AbstractBuild upstreamBuild = getBuildByUpstreamCause(build.getCauses());
        if(upstreamBuild!= null) {
            accountableBuilds.add(upstreamBuild);
            ChangeLogSet<? extends Entry> changeSet = upstreamBuild.getChangeSet();
            if(listener != null ) listener.getLogger().append("[ci-game] UpStream Build ID: " + upstreamBuild.getId()+ "\n");
            if(listener != null ) listener.getLogger().append("[ci-game] UpStream Display Name: " + upstreamBuild.getFullDisplayName()+ "\n");
            if(listener != null ) listener.getLogger().append("[ci-game] Is UpStream Change Set Empty: " + changeSet.isEmptySet() + "\n");

        }
        
        // also add all previous aborted builds:
        Run<?, ?> previousBuild = build.getPreviousBuild();
        while (previousBuild != null && previousBuild.getResult() == Result.ABORTED) {
        	accountableBuilds.add(previousBuild);
        	previousBuild = previousBuild.getPreviousBuild();
        }

        Set<User> players = new TreeSet<User>(usernameIsCasesensitive ? null : new UsernameCaseinsensitiveComparator());
        for (Run<?, ?> b : accountableBuilds) {
            players.addAll(ChangeLogRetriever.getChangeLogUsers(b));
        }
        
        return updateUserScores(players, sc.getTotalPoints(), accountableBuilds, listener);
    }
    private AbstractBuild getBuildByUpstreamCause(List<Cause> causes) {
        for(Cause cause: (List<Cause>) causes){
            if(cause instanceof Cause.UpstreamCause) {
                TopLevelItem upstreamProject = Hudson.getInstance().getItemByFullName(((Cause.UpstreamCause)cause).getUpstreamProject(), TopLevelItem.class);
                if(upstreamProject instanceof AbstractProject){
                    int buildId = ((Cause.UpstreamCause)cause).getUpstreamBuild();
                    Run run = ((AbstractProject) upstreamProject).getBuildByNumber(buildId);
                    System.out.println();
                    AbstractBuild upstreamRun = getBuildByUpstreamCause(run.getCauses());
                    if(upstreamRun == null) {
                        return (AbstractBuild) run;
                    }else{
                        return upstreamRun;
                    }
                }
            }
        }
        return null;

    }

    /**
     * Add the score to the users that have committed code in the change set
     * 
     *
     * @param score the score that the build was worth
     * @param accountableBuilds the builds for which the {@code score} is awarded for.
     * @throws IOException thrown if the property could not be added to the user object.
     * @return true, if any user scores was updated; false, otherwise
     */
    private boolean updateUserScores(Set<User> players, double score, List<Run<?, ?>> accountableBuilds, TaskListener listener) throws IOException {
        if (score != 0) {
            for (User user : players) {
                UserScoreProperty property = user.getProperty(UserScoreProperty.class);
                if (property == null) {
                    property = new UserScoreProperty();
                    user.addProperty(property);
                }
                if (property.isParticipatingInGame()) {
                    property.setScore(property.getScore() + score);
                    property.rememberAccountableBuilds(accountableBuilds, score);
                }
                user.save();
            }
        }
        return (!players.isEmpty());
    }

    public static class UsernameCaseinsensitiveComparator implements Comparator<User> {
        public int compare(User arg0, User arg1) {
            return arg0.getId().compareToIgnoreCase(arg1.getId());
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
}
