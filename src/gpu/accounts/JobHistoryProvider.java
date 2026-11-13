package gpu.accounts;

import java.util.*;

//Gets a student's job history from the job management code.
public interface JobHistoryProvider {

	// Returns the job details for the student with this university ID.
    List<String> getJobsForStudent(String universityId);
}