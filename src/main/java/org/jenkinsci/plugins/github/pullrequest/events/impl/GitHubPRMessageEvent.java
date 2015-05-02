package org.jenkinsci.plugins.github.pullrequest.events.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRCause;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRPullRequest;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRRepository;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRTrigger;
import org.jenkinsci.plugins.github.pullrequest.events.GitHubPREvent;
import org.jenkinsci.plugins.github.pullrequest.events.GitHubPREventDescriptor;
import org.jenkinsci.plugins.github.pullrequest.restrictions.GitHubPRUserRestriction;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Trigger PR based on comment status
 *
 * @author Kanstantsin Shautsou
 */
public class GitHubPRMessageEvent extends GitHubPREvent {
    private static final String DISPLAY_NAME = "Comment message";
    private final static Logger LOGGER = Logger.getLogger(GitHubPRMessageEvent.class.getName());

    private String runMsg = ".*test\\W+this\\W+please.*";

    public String getRunMsg() {
        return runMsg;
    }

    @DataBoundConstructor
    public GitHubPRMessageEvent(String runMsg) {
        this.runMsg = runMsg;
    }

    @Override
    public GitHubPRCause isStateChanged(GitHubPRTrigger gitHubPRTrigger, GHPullRequest remotePR,
                                        @CheckForNull GitHubPRPullRequest localPR, TaskListener listener) {
        if (localPR == null || localPR.getLastCommentCreatedAt() == null) {
            return null; // nothing to compare
        }
        final PrintStream logger = listener.getLogger();

//        LOGGER.log(Level.FINE, "Checking for new messages...");

        GitHubPRCause cause = null;
        try {
            for (GHIssueComment comment : remotePR.getComments()) {
                if (localPR.getLastCommentCreatedAt().compareTo(comment.getCreatedAt()) < 0) {
                    logger.println(DISPLAY_NAME + ": state has changed (new comment found - \""
                            + comment.getBody() + "\")");
                    cause = checkComment(comment, gitHubPRTrigger.getUserRestriction(), remotePR);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Couldn''t obtain comments: {0}", e.getMessage());
        }
        return cause;
    }

    private GitHubPRCause checkComment(GHIssueComment comment,
                                       GitHubPRUserRestriction userRestriction,
                                       GHPullRequest remotePR) {
        GitHubPRCause cause = null;
        try {
            String body = comment.getBody();

            if ((userRestriction == null || userRestriction.isWhitelisted(comment.getUser()))
                    && Pattern.compile(runMsg).matcher(body).matches()) {
                LOGGER.log(Level.FINEST, "Triggering by comment '{0}'", body);
                cause = new GitHubPRCause(remotePR, remotePR.getUser(), "PR was triggered by comment", null, null);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
        }
        return cause;
    }

    @Extension
    public static class DescriptorImpl extends GitHubPREventDescriptor {
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
    }
}
