package gpu.accounts;

import java.io.*;

// Stores a student's GPU allowance in whole minutes.
//Tracks the student's GPU minutes before, during and after a job.
public final class TimeAllowance implements Serializable {
    private static final long serialVersionUID = 1L;

    private int allocated; // Total minutes assigned to the student.
    private int used;      // Minutes already charged.
    private int reserved;  // Minutes held for accepted jobs.

    public TimeAllowance(int allocated) {
        setAllocated(allocated);
    }

    public int getAllocated() {
        return allocated;
    }

    public int getUsed() {
        return used;
    }

    public int getReserved() {
        return reserved;
    }

    // Calculates the minutes available for new jobs.
    public int getAvailable() {
        return allocated - used - reserved;
    }
    
    // Stops the admin from taking away minutes already spent or reserved to accepted jobs.
    public void setAllocated(int minutes) {
        if (minutes < 0 || (long) minutes < (long) used + reserved) {
            throw new IllegalArgumentException(
                    "Allocation cannot be below used + reserved minutes."
            );
        }

        allocated = minutes;
    }

    // Adds extra minutes to the current allocation.
    public void grant(int minutes) {
        // Also prevents the total from exceeding the integer limit.
        if (minutes <= 0
                || (long) allocated + minutes > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    "Enter a valid positive grant."
            );
        }

        allocated += minutes;
    }

    // Reserves the full duration of a newly accepted job.
    public void reserve(int minutes) {
    	// A job can use all the student's remaining minutes.
        if (minutes <= 0 || minutes > getAvailable()) {
            throw new IllegalArgumentException(
                    "Insufficient available minutes."
            );
        }

        reserved += minutes;
    }

    // Releases a job's reservation and adds its charge to used minutes.
    public void settle(int reservation, int charge) {
        if (reservation <= 0
                || reservation > reserved
                || charge < 0
                || charge > reservation) {

            throw new IllegalArgumentException(
                    "Invalid reservation or charge."
            );
        }

        reserved -= reservation;
        used += charge;
    }
}