package hudson.plugins.cigame;

import java.util.*;

import hudson.model.*;
import hudson.plugins.cigame.util.ChangeLogRetriever;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.plugins.cigame.model.ScoreCard;

/**
 * Score card for a certain build
 * 
 * @author Erik Ramfelt
 */
@ExportedBean(defaultVisibility = 999)
public class ScoreCardAction implements Action {

    private static final long serialVersionUID = 1L;

    private Run<?, ?> build;

    private ScoreCard scorecard;

    public ScoreCardAction(ScoreCard scorecard, Run<?, ?> b) {
        build = b;
        this.scorecard = scorecard;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    public String getDisplayName() {
        return Messages.Scorecard_Title(); //$NON-NLS-1$
    }

    public String getIconFileName() {
        return GameDescriptor.ACTION_LOGO_MEDIUM;
    }

    public String getUrlName() {
        return "cigame"; //$NON-NLS-1$
    }

    @Exported
    public ScoreCard getScorecard() {
        return scorecard;
    }

    @Exported
    public Collection<User> getParticipants() {
        return getParticipants(Hudson.getInstance().getDescriptorByType(GameDescriptor.class).getNamesAreCaseSensitive());
    }
    
    Collection<User> getParticipants(boolean usernameIsCasesensitive) {
        Comparator<User> userIdComparator = new CaseInsensitiveUserIdComparator();
        List<User> players = new ArrayList<User>();
        List<User> authors = ChangeLogRetriever.getChangeLogUsers(build);
        for (User user : authors) {
            UserScoreProperty property = user.getProperty(UserScoreProperty.class);
            if ((property != null) 
                    && property.isParticipatingInGame() 
                    && (usernameIsCasesensitive || Collections.binarySearch(players, user, userIdComparator) < 0)) {
                players.add(user);
            }
        }
        Collections.sort(players, new UserDisplayNameComparator());
        return players;
    }
    
    private static class UserDisplayNameComparator implements Comparator<User> {
        public int compare(User arg0, User arg1) {
            return arg0.getDisplayName().compareToIgnoreCase(arg1.getDisplayName());
        }            
    }
}
