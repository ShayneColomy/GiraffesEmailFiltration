package v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Message.RecipientType;
import javax.mail.internet.MimeMultipart;

public class EmailReceiver
{
	public final Logger logger = Logger.getLogger(EmailReceiver.class.getName());

	public final String PROTOCOL = "imap";
	public final String HOST = "imap.gmail.com";
	public final String PORT = "993";

	public String messageContent, contentType, toList, ccList, subject, sentDate, from;


	public Filter filter;
	public ArrayList<String> recentMail = new ArrayList<String>();

	public EmailReceiver() {

	}

	public EmailReceiver(Filter filter)
	{
		this.filter = filter;
	}

	/**
	 * setProperties() sets the host, port and protocol for the email server connection.
	 * @return The properties for connection.
	 */
	private Properties setProperties()
	{
		Properties p = new Properties();
		p.put("mail." + PROTOCOL + ".host", HOST);
		p.put("mail." + PROTOCOL + ".port", PORT);

		p.setProperty("mail." + PROTOCOL + ".socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		p.setProperty("mail." + PROTOCOL + ".soceketFactory.fallback", "false");
		p.setProperty("mail." + PROTOCOL + ".socketFactory.port", PORT);

		return p;
	}

	/**
	 * checkEmail() Accesses user email account with password and returns the messages.
	 * @param user username for email account.
	 * @param password password for email account.
	 * @return an array of message objects.
	 */
	public ArrayList<String> checkEmail (String user, String password, Filter filter)//added Filter
	{
		Properties p = setProperties();
		Session session = Session.getDefaultInstance(p);
		Message[] messages = null;
		ArrayList<String> newMail = new ArrayList<String>();
		try
		{
			Store s = session.getStore(PROTOCOL);
			s.connect(user, password);
			Folder inbox = s.getFolder("INBOX");
			inbox.open(Folder.READ_ONLY);

			messages = inbox.getMessages();

			newMail = extractComponents(messages, filter);// with Filter object
			// disconnect
			inbox.close(false);
			s.close();
		} catch (NoSuchProviderException ex) {
			System.out.println("No provider for protocol: " + PROTOCOL);
			ex.printStackTrace();
		} catch (MessagingException ex) {
			System.out.println("Could not connect to the message store");
			ex.printStackTrace();
		}
		return newMail;
	}


	/**
	 * extractComponents() assigns string representations of the different pieces of 
	 * an email message to class variables
	 * @param messages The array of messages from the inbox being checked.
	 * @throws MessagingException 
	 */
	//added Filter parameter
	public ArrayList<String> extractComponents(Message[] messages, Filter filter) throws MessagingException 
	{
		ArrayList<String> recentMail = new ArrayList<String>();
		for(Message m: messages)
		{
			if(filter.getLastCheck().compareTo(m.getSentDate()) < 0)//added: check date before continuing.
			{
				messageContent = "";
				contentType = m.getContentType();
				Address[] fromAddress = m.getFrom();
				from = fromAddress[0].toString();

				if (contentType.toUpperCase().contains("TEXT/PLAIN")
						|| contentType.toUpperCase().contains("TEXT/HTML")) 
				{
					try
					{
						messageContent = m.getContent().toString();
					}
					catch(Exception e)
					{
						messageContent = "--->>>Error loading message<<<---";
					}
				}
				else
				{
					try {
						messageContent = getTextFromMessage(m);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}



				//logger.log(Level.INFO, "message type: " + m.getClass().getSimpleName());//ME

				subject = m.getSubject();//0
				toList = String.join(", ", parseAddresses(m.getRecipients(RecipientType.TO)));
				ccList = String.join(", ", parseAddresses(m.getRecipients(RecipientType.CC)));
				sentDate = m.getSentDate().toString();


				logger.log(Level.INFO, EmailParser.textFromHtml(messageContent));
				// print out details of each message
				//System.out.println("Message #" + (i + 1) + ":");
				if(//lastCheck.compareTo(m.getSentDate()) < 0 &&
						isValidSender(EmailParser.extractSender(from), 
								EmailParser.textFromHtml(messageContent), filter)) 
				{

					recentMail.add(messageContent);
					System.out.println("\t From: " + EmailParser.extractSender(from));
					System.out.println("\t To: " + toList);
					System.out.println("\t CC: " + ccList);
					System.out.println("\t Subject: " + subject);
					System.out.println("\t Sent Date: " + sentDate);
					System.out.println("\t Message: " + EmailParser.textFromHtml(messageContent));
				}
			}
		}
		logger.log(Level.INFO, recentMail.toString());
		return recentMail;
	}

	/**
	 * isValidSender() checks if the sender is on the user's list of pre-approved senders.
	 * @param sender: sender of the message.
	 * @return true if on sender list, false otherwise.
	 */
	public boolean isValidSender(String sender, String message, Filter filter)//added Filter
	{
		for(String s: filter.getEmailFilter().keySet())//using Filter 
		{
			if(sender.contains(s))
			{
				if(Pattern.compile(filter.getEmailFilter().get(s)).matcher(message).find())// using Filter
				{
					return true;
				}
			}
		}
		return false;
	}
	/**
	 * parseAddresses() turns an array of Address objects into a string, seperated by commas.
	 * @param address The address component of a message object.
	 * @return String representation of email addresses.
	 */
	private String[] parseAddresses(Address[] address) 
	{
		if(address == null) 
		{
			return new String[] {""};
		}
		String[] s = new String[address.length];
		for(int i = 0; i < s.length; i++)
		{
			s[i] = address[i].toString();
		}
		return s;
	}


	/*
	 * @param Takes in a Type Message and determins the content type, if Text/plain return the result, if
	 * a multipart boy call getTextFromMultipart
	 * @returns a the email body of type string 
	 */
	private String getTextFromMessage(Message message) throws MessagingException, IOException 
	{
		String result = "";
		if (message.isMimeType("text/plain")) {
			result = message.getContent().toString();
		} else if (message.isMimeType("multipart/*")) {
			MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
			result = getTextFromMimeMultipart(mimeMultipart);
		}
		return result;
	}

	/*
	 * @param takes in a type Message
	 * @returns a multipart email body converted to text and returns a type string.
	 */
	private String getTextFromMimeMultipart(MimeMultipart mimeMultipart)  throws MessagingException, IOException
	{
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				result = result + bodyPart.getContent();
				break; // without break same text appears two times
			} else if (bodyPart.isMimeType("text/html")) {
				String html = (String) bodyPart.getContent();
				result = result  + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart){
				result = result + getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
			}
		}
		return result;
	}

} 