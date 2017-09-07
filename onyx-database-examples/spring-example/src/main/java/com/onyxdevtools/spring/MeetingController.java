package com.onyxdevtools.spring;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.spring.entities.Meeting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
class MeetingController
{
    // Persistence Manager injected by spring
    @Autowired
    private PersistenceManager persistenceManager;

    /**
     * Simple method used to encapsulate the saving of a meeting.
     * @param meeting Meeting to persist
     */
    void saveMeeting(Meeting meeting)
    {
        try {
            persistenceManager.saveEntity(meeting);
        } catch (OnyxException e) {
            // Log an error
        }
    }

    /**
     * Method used to stream all meetings at work that are
     * snoozers and are really hard to stay awake but, you still have
     * to pay attention because someone is going to call on you and ask
     * you a dumb question.
     *
     * @return A list of really boring meetings
     */
    @SuppressWarnings("unchecked")
    List<Meeting> findBoringMeetings()
    {
        Query query = new Query(Meeting.class, new QueryCriteria("notes", QueryCriteriaOperator.CONTAINS, "Boring"));
        List<Meeting> boringMeetings = null;
        try {
            boringMeetings = persistenceManager.executeQuery(query);
        } catch (OnyxException e) {
            // Log an error
        }
        return boringMeetings;
    }
}
