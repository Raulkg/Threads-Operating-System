
/**********************************************************************************
	CIS 657.M001 Principles of Operating Systems Lab Assignment
	Project 2: Threads
	Dependencies: TimerInterruptHandler.java

	Group Members:
		Kranthi Kumar Polisetty
		Rahul Kumar Gaddam
		Rao Yan
		Sujit Poudel
		
	Project Goal: The goal of this project is to implement Threads object for
	simulated OSP 2 Operating systems, given all other complete modules in OSP.
***********************************************************************************/

/***************************************************************************************************************************
	We utilize ArrayList(of type ThreadCB) as the data structure to handle the ready queue of Threads, which is an static 
	member of ThreadsCB object. Arraylist is capable of modifying elements in the list dynamicly without interferring a 
	simultaneous iteration. This approuch satisfied the assignment criteria and carries simplistic yet intuitive implementation.
	do_Create() Creates new TaskCB object by invoking the constructor. Due to absence of explicit instruction in the 
	assignment description, priorities of the new threads has been set to an arbitrary number 0. 
	Should there be any Error or Warning occured during the execution of OSP2, relavant messages would be output to console. 
	
	The scheduling alsorithm used here is based on Round-robin with time quanta provided
	by static variable TimeQuanta. By default it is set to 2
****************************************************************************************************************************/


/*
		Feel free to add local classes to improve the readability of your code
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package osp.Threads;

import java.util.ArrayList;
import java.util.List;
import static osp.IFLModules.IflThreadCB.*;
import osp.Tasks.TaskCB;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
 *
 * @OSPProject ThreadsCB
 */
public class ThreadCB extends IflThreadCB 
{
	static List<ThreadCB> ReadyQueue; //The list of files that the task is utilizing
	private static int TimeQuanta = 2; //Unit time for round robin
	/**
		The thread constructor. Must call super(); as its first statement.
		@OSPProject Threads
	*/
	public ThreadCB()
	{
		// your code goes here
		//We need to initialize our Queue and call super(); upon instance creation
		super();
	}

	/**
		This method will be called once at the beginning of the
		simulation. The student can set up static variables here.
		@OSPProject Threads
	*/
	public static void init()
	{
		ReadyQueue = new ArrayList<>();
			// your code goes here
			//Nothing to do here...
	}

	/** 
			Sets up a new thread and adds it to the given task. 
			The method must set the ready status 
			and attempt to add thread to task. If the latter fails 
			because there are already too many threads in this task, 
			so does this method, otherwise, the thread is appended 
			to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

			@OSPProject Threads
	*/
	static public ThreadCB do_create(TaskCB task)
	{
			// your code goes here
		//Need to check if we are within limit here
		if (task.getThreadCount() >= MaxThreadsPerTask)
		{
			ThreadCB.dispatch();
			return null;
		}
		//Create new ThreadCB object
		ThreadCB CurrentThread = new ThreadCB();
		//Link thread to a task
		if(task.addThread(CurrentThread) == FAILURE) return null;
		CurrentThread.setTask(task);
		//setting priority to 0 as arbitrary number, as we don't care at this moment
		CurrentThread.setPriority(0);
		//set new status
		CurrentThread.setStatus(ThreadReady);
		//put at readyqueue end
		ReadyQueue.add(CurrentThread);
		//Dispatch the new CurrentThread
		ThreadCB.dispatch();
		return CurrentThread;
	}

	/** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
			
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.

	@OSPProject Threads
	*/
	public void do_kill()
	{
		// your code goes here
		TaskCB TaskForCurrentThread = this.getTask();
		if (this.getStatus() == ThreadKill){} //Nothing to do here...
		else if (this.getStatus() == ThreadReady)
		{
			this.ReadyQueue.remove(this);
		}
		else if (this.getStatus() == ThreadRunning)
		{
			MMU.getPTBR().getTask().setCurrentThread(null);
			MMU.setPTBR(null);
		}
		else
		{
			for(int i=0; i < Device.getTableSize(); i++) Device.get(i).cancelPendingIO(this);
		}
		this.setStatus(ThreadKill);
		TaskForCurrentThread.removeThread(this);
		ResourceCB.giveupResources(this);
		//If this is the parent thread of the assiciated task, we need to kill off the task as well
		if(TaskForCurrentThread.getThreadCount() == 0) TaskForCurrentThread.kill();
		ThreadCB.dispatch();
	}

	/** Suspends the thread that is currenly on the processor on the 
			specified event. 

			Note that the thread being suspended doesn't need to be
			running. It can also be waiting for completion of a pagefault
			and be suspended on the IORB that is bringing the page in.

	Thread's status must be changed to ThreadWaiting or higher,
			the processor set to idle, the thread must be in the right
			waiting queue, and dispatch() must be called to give CPU
			control to some other thread.

	@param event - event on which to suspend this thread.

			@OSPProject Threads
	*/
	public void do_suspend(Event event)
	{
		if (this.getStatus() == ThreadKill){} //Nothing to do here...
		else if (this.getStatus() == ThreadReady) {}
		else if (this.getStatus() == ThreadRunning)
		{
			event.addThread(MMU.getPTBR().getTask().getCurrentThread());
			MMU.getPTBR().getTask().getCurrentThread().setStatus(ThreadWaiting);
			MMU.getPTBR().getTask().setCurrentThread(null);
			MMU.setPTBR(null);
		}
		else
		{ //Thread is either already in suspended mode or is waiting
			event.removeThread(this);
			this.setStatus(this.getStatus()+1);
			event.addThread(this);
		}
		ThreadCB.dispatch();
	}

	/** Resumes the thread.
			
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.

	@OSPProject Threads
	*/
	public void do_resume()
	{
		if (this.getStatus() == ThreadKill){} //Nothing to do here...
		else if (this.getStatus() == ThreadReady) { return; }
		else if (this.getStatus() == ThreadWaiting)
		{
			this.setStatus(ThreadReady);
			ReadyQueue.add(this);
		}
		else
		{ //This is threadWaiting + n case
			this.setStatus(this.getStatus()-1);
		}
		ThreadCB.dispatch();
	}

	/** 
			Selects a thread from the run queue and dispatches it. 
			If there is just one theread ready to run, reschedule the thread 
			currently on the processor.
			In addition to setting the correct thread status it must
			update the PTBR.
	@return SUCCESS or FAILURE
			@OSPProject Threads
	*/
	public static int do_dispatch()
	{
		// your code goes here
		
		//Time quanta for current thread is not yet done
		if (MMU.getPTBR() != null)
		{
			if(MMU.getPTBR().getTask().getCurrentThread().getTimeOnCPU()%TimeQuanta < 1)
				return SUCCESS;
		}
		//Get first thread in the ready queue
		ThreadCB FirstThreadInReadyQueue;
		if (!ReadyQueue.isEmpty())
		{
			FirstThreadInReadyQueue = (ThreadCB) ReadyQueue.get(0);//removeHead();
			ReadyQueue.remove(0);
		}
		else
			FirstThreadInReadyQueue = null;

		//Context Switching
		if (FirstThreadInReadyQueue != null)
		{
			if(MMU.getPTBR() != null)//Stuff running. So need to preempt
			{
				ReadyQueue.add(MMU.getPTBR().getTask().getCurrentThread());
				MMU.getPTBR().getTask().getCurrentThread().setStatus(ThreadReady);
				MMU.getPTBR().getTask().setCurrentThread(null);
				MMU.setPTBR(null);

				//Finally do the actual dispatch
				FirstThreadInReadyQueue.setStatus(ThreadRunning);
				MMU.setPTBR(FirstThreadInReadyQueue.getTask().getPageTable());
				FirstThreadInReadyQueue.getTask().setCurrentThread(FirstThreadInReadyQueue);
				return SUCCESS;
			}
			else //Set the thread to CPU
			{
				FirstThreadInReadyQueue.setStatus(ThreadRunning);
				MMU.setPTBR(FirstThreadInReadyQueue.getTask().getPageTable());
				FirstThreadInReadyQueue.getTask().setCurrentThread(FirstThreadInReadyQueue);
				return SUCCESS;
			}
		}
		else
		{
			if(MMU.getPTBR() != null) //There is no thread to dispatch, so current thread will continue
			{
				return SUCCESS;
			}
			else
			{
				return FAILURE;
			}
		
		}
	}

	/**
			Called by OSP after printing an error message. The student can
			insert code here to print various tables and data structures in
			their state just after the error happened.  The body can be
			left empty, if this feature is not used.

			@OSPProject Threads
	*/
	public static void atError()
	{
		// your code goes here
		System.out.println("Error occured at Threads");
	}

	/** Called by OSP after printing a warning message. The student
			can insert code here to print various tables and data
			structures in their state just after the warning happened.
			The body can be left empty, if this feature is not used.
			
			@OSPProject Threads
		*/
	public static void atWarning()
	{
		// your code goes here
		System.out.println("Warning from Threads");
	}


	/*
			Feel free to add methods/fields to improve the readability of your code
	*/
}
