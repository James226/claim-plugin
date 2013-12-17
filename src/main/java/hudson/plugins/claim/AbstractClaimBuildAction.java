package hudson.plugins.claim;

import hudson.model.BuildBadgeAction;
import hudson.model.Hudson;
import hudson.model.ProminentProjectAction;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.tasks.Mailer;
import hudson.tasks.junit.TestAction;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
 
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;

import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 2)
public abstract class AbstractClaimBuildAction<T extends Saveable> extends TestAction implements BuildBadgeAction,
		ProminentProjectAction {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(AbstractClaimBuildAction.class.getName());

	private boolean claimed;
	private String claimedBy;
	private Date claimDate;
	private boolean transientClaim;
	
	protected T owner;
	
	AbstractClaimBuildAction(T owner) {
		this.owner = owner;
	}

	private String reason;

	public String getIconFileName() {
		return null;
	}

	public String getUrlName() {
		return "claim";
	}

	public void doClaim(StaplerRequest req, StaplerResponse resp)
			throws ServletException, IOException {
		Authentication authentication = Hudson.getAuthentication();
    String currentUsername = authentication.getName();
		String name = (String) req.getSubmittedForm().get("who");
    if (StringUtils.isEmpty(name)) {
      name = currentUsername;
    } else {
      // Validate the specified username.
      User assignedToUser = User.get(name, false);
      if (assignedToUser == null) {
        LOGGER.log(Level.WARNING, "Invalid username specified for assignment: "+name);
        resp.forwardToPreviousPage(req);
        return;
      }
    }
		String reason = (String) req.getSubmittedForm().get("reason");
		boolean sticky = req.getSubmittedForm().getBoolean("sticky");
		if (StringUtils.isEmpty(reason)) reason = null;
		claim(name, reason, sticky);
		owner.save();
		if (!currentUsername.equals(name)) {
			sendAssignmentEmail(name, currentUsername, reason);
		}
		resp.forwardToPreviousPage(req);
	}
	
	private void sendAssignmentEmail(String assignee, String assignedBy, String reason) {
	  try {
	    User assigneeUser = User.get(assignee, false);
	    String assigneeEmail = assigneeUser.getProperty(Mailer.UserProperty.class).getAddress();
	    String claimedItemDisplayName = getClaimedItemDisplayName();
	    String claimedItemUrl = Mailer.descriptor().getUrl()+getClaimedItemUrl();

	    MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());
	    msg.setFrom(new InternetAddress(Mailer.descriptor().getAdminAddress()));
	    msg.setRecipient(Message.RecipientType.TO, new InternetAddress(assigneeEmail));
	    msg.setSentDate(new Date());
	    msg.setSubject(getNoun()+" assigned to you: "+claimedItemDisplayName);

	    String msgContent = assignedBy + " assigned you this "+getNoun()+": "+claimedItemDisplayName;
	    msgContent += "\n\nReason: "+reason;
	    msgContent += "\n\n"+claimedItemUrl;
	    msg.setContent(msgContent, "text/plain");

	    Transport.send(msg);
	  } catch (Exception e) {
	    LOGGER.log(Level.WARNING, "Could not send assignment email to "+assignee, e);
	  }
	}
	protected abstract String getClaimedItemDisplayName();
	protected abstract String getClaimedItemUrl();

	public void doUnclaim(StaplerRequest req, StaplerResponse resp)
			throws ServletException, IOException {
		unclaim();
		owner.save();
		resp.forwardToPreviousPage(req);
	}

	@Exported
	public String getClaimedBy() {
		return claimedBy;
	}

	public String getClaimedByName() {
		User user = User.get(claimedBy, false);
		if (user != null) {
			return user.getDisplayName();
		} else {
			return claimedBy;
		}
	}
	
	public void setClaimedBy(String claimedBy) {
		this.claimedBy = claimedBy;
	}

	@Exported
	public boolean isClaimed() {
		return claimed;
	}

	public void claim(String claimedBy, String reason, boolean sticky) {
		this.claimed = true;
		this.claimedBy = claimedBy;
		this.reason = reason;
		this.transientClaim = !sticky;
		this.claimDate = new Date();
	}
	
	/**
	 * Claim a new Run with the same settings as this one.
	 */
	public void copyTo(AbstractClaimBuildAction other) {
		other.claim(getClaimedBy(), getReason(), isSticky());
	}

	public void unclaim() {
		this.claimed = false;
		this.claimedBy = null;
		this.transientClaim = false;
		this.claimDate = null;
		// we remember the reason to show it if someone reclaims this build.
	}

	public boolean isClaimedByMe() {
		return !isUserAnonymous()
				&& Hudson.getAuthentication().getName().equals(claimedBy);
	}

	public boolean canClaim() {
		return !isUserAnonymous() && !isClaimedByMe();
	}

	public boolean canRelease() {
		return !isUserAnonymous() && isClaimedByMe();
	}

	public boolean isUserAnonymous() {
		return Hudson.getAuthentication().getName().equals("anonymous");
	}

	@Exported
	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public boolean hasReason() {
		return !StringUtils.isEmpty(reason);
	}

	public boolean isTransient() {
		return transientClaim;
	}

	public void setTransient(boolean transientClaim) {
		this.transientClaim = transientClaim;
	}
	
	public boolean isSticky() {
		return !transientClaim;
	}
	
	public void setSticky(boolean sticky) {
		this.transientClaim = !sticky;
	}

    @Exported
	public Date getClaimDate() {
		return this.claimDate;
	}

	public boolean hasClaimDate() {
		return this.claimDate != null;
	}
	
	public abstract String getNoun();
	
}
