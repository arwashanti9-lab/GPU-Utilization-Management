package gpu.scheduling;

import gpu.accounts.AccountService;
import gpu.accounts.Role;
import gpu.accounts.User;
import java.io.IOException;
import java.util.List;


// supposed to connect the job actions to the account and scheduler classes
public class JobService {
  //Attributes
  private final AccountService accounts;
  private final Scheduler scheduler;

  //Constructors
  //both are a requirement to fuilfil the condition so either being null would throw an exception
public JobService(AccountService accounts, Scheduler scheduler) {  
      if (accounts == null || scheduler == null) {
            throw new IllegalArgumentException("Accounts and scheduler are required.");
        }
        this.accounts = accounts;
        this.scheduler = scheduler;
    }
//Operations
// student submits a job, whoever calls this must save the scheduler after
 public String submitJob(String title, String application, String description, int duration) throws IOException {
        // only a logged/registered in student can submit
        User user = accounts.getCurrentUser();
        if (user == null || user.getRole() != Role.STUDENT) {
            throw new SecurityException("A student must be logged in to submit a job.");
        }
        String studentId = user.getUsername();
        String jobId = nextJobId();
//condition1:check the job is valid(no change)
  scheduler.validateSubmission(jobId, studentId, title, application, description, duration);
//condition2: hold the money for the job from the student's account
  accounts.reserveForJob(studentId, jobId, duration);
//condition3:now add the job to the scheduler
  scheduler.acceptReservedJob(jobId, studentId, title, application, description, duration);
        return jobId;
    }

//when scheduler cancels the job, money is settled through this method
  public void cancelJob(String jobId, String reason) throws IOException {
    User user= accounts.getCurrentUser();
    if (user == null) {
      throw new SecurityException("Login is required.");}
//admin can cancel any job, student only for themselves
  boolean admin = user.getRole() == Role.ADMINISTRATOR;
        scheduler.cancel(jobId, user.getUsername(), admin, reason);
// after cancelling, charge only what the job used and return the rest
  Job job = findJob(jobId);
        accounts.settleForJob(job.getId(), job.getCharged());
    }
//admin movers time forward 1 min and settles jobs that just finished
  public void advanceMinute() throws IOException {
    User user = accounts.CurrentUser();
    if(user==null || user.getRole() != Role.ADMINISTRATOR){
      throw new SecurityException("Administrator login is required to advance time.");}

    //take a copy of the jobs before and after the minute
    List <Job> before = scheduler.getJobs();
    scheduler.advanceMinute();
    List <Job> after = scheduler.getJobs();

    //add jobs that were prev running and completed now
    for (Job previous : before) {
            if (previous.getStatus() == Job.Status.RUNNING) {
                for (Job current : after) {
                    if (current.getId().equals(previous.getId())
                            && current.getStatus() == Job.Status.COMPLETED) {
                        accounts.settleForJob(current.getId(), current.getCharged());
                    }
                }
            }
        }
    }
  //Helpers
  //matches job with id 
  private Jov findJob(String id){
    for(Job job: scheduler.getJobs())if (job.getId().equals(id.trim())) {
                return job;
            }
        }
        throw new IllegalArgumentException("Job not found.");
    }

//formats the id as JOB-000000 , the number will maintain the six digits to stay accurate
  private String nextJobId(){
    int highest=0;
    for (Job job : scheduler.getJobs()){
      String id = job.getId();
            // only look at ids in the format JOB-######
            if (id.matches("JOB-[0-9]{6}")) {
                int number = Integer.parseInt(id.substring(4)); // remove "JOB-"
                if (number > highest) {
                    highest = number;
                }
            }
        }
// 999999 is the highest it can go
    if (highest>=999999){
      throw new IllegalStateException("No more job IDs available.");
       }
        return String.format("JOB-%06d", highest + 1);
    }
      
