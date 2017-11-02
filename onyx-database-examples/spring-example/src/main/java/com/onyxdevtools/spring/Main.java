package com.onyxdevtools.spring;

import com.onyxdevtools.spring.entities.Meeting;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static java.lang.System.exit;

@SuppressWarnings("WeakerAccess")
@SpringBootApplication
@EnableAutoConfiguration
public class Main
{
    @SuppressWarnings("SpellCheckingInspection")
    public static void main(String[] args)
    {
        // Run the spring application and return the context
        ApplicationContext ctx = SpringApplication.run(Main.class, args);

        // The the Meeting controller that is annotated using @Controller
        // Within the Meeting controller the persistence manager is autowired
        MeetingController meetingController = ctx.getBean(MeetingController.class);

        // Create some sample data that depicts some meetings you had today.
        Meeting dailyStandup = new Meeting();
        dailyStandup.setDescription("Meeting that is supposed to be 15 minutes but never really is.");
        dailyStandup.setLocation("Aspen"); // Because every office has a meeting room called Aspen for some reason
        dailyStandup.setNotes("Kinda Boring but mostly comatose because I was busy working all night on fun Onyx work");

        Meeting bugTriage = new Meeting();
        bugTriage.setDescription("Meeting for QA to air their grievances");
        bugTriage.setLocation("Telluride");
        bugTriage.setNotes("Really Really Boring");

        Meeting dataArchitectureDiscussion = new Meeting();
        dataArchitectureDiscussion.setDescription("Exciting meeting about implementing a new database that will make my life easier called Onyx");
        dataArchitectureDiscussion.setLocation("Dev Studio");
        dataArchitectureDiscussion.setNotes("Exciting!!!");

        meetingController.saveMeeting(dailyStandup);
        meetingController.saveMeeting(bugTriage);
        meetingController.saveMeeting(dataArchitectureDiscussion);

        List<Meeting> booringMeetings = meetingController.findBoringMeetings();

        assert booringMeetings.size() == 2;

        // Exit the Spring boot application.
        exit(0);
    }
}
