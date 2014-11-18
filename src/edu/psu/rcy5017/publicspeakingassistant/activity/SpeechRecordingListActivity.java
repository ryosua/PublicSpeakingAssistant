package edu.psu.rcy5017.publicspeakingassistant.activity;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.ericharlow.DragNDrop.DragNDropAdapter;
import com.ericharlow.DragNDrop.DragNDropListView;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.psu.rcy5017.publicspeakingassistant.AudioCntl;
import edu.psu.rcy5017.publicspeakingassistant.R;
import edu.psu.rcy5017.publicspeakingassistant.constant.DefaultValues;
import edu.psu.rcy5017.publicspeakingassistant.constant.Misc;
import edu.psu.rcy5017.publicspeakingassistant.constant.RequestCodes;
import edu.psu.rcy5017.publicspeakingassistant.datasource.SpeechRecordingDataSource;
import edu.psu.rcy5017.publicspeakingassistant.listener.DragListenerImpl;
import edu.psu.rcy5017.publicspeakingassistant.listener.DropReorderListener;
import edu.psu.rcy5017.publicspeakingassistant.listener.RemoveListenerImpl;
import edu.psu.rcy5017.publicspeakingassistant.model.SpeechRecording;
import edu.psu.rcy5017.publicspeakingassistant.task.DeleteTask;
import edu.psu.rcy5017.publicspeakingassistant.task.GetAllTask;
import edu.psu.rcy5017.publicspeakingassistant.task.RenameSpeechRecordingTask;

public class SpeechRecordingListActivity extends ListActivity {
    
    private static final String TAG = "SpeechRecordingListActivity";
    
    private DragNDropAdapter<SpeechRecording> adapter;
    private SpeechRecordingDataSource datasource;
    private long speechID;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_recording_list);
        
        datasource = new SpeechRecordingDataSource(this);
        
        // Get the speechID passed from list activity.
        final Intent intent = this.getIntent();
        speechID = intent.getLongExtra("id", DefaultValues.DEFAULT_LONG_VALUE);
       
        try {
            final List<SpeechRecording> values = new GetAllTask<SpeechRecording>(datasource, speechID).execute().get();
            
            adapter = new DragNDropAdapter<SpeechRecording>(this, new int[]{R.layout.dragitem}, new int[]{R.id.TextView01}, values);
            setListAdapter(adapter);
            
            final ListView listView = getListView();
            if (listView instanceof DragNDropListView) {
                ((DragNDropListView) listView).setDropListener(new DropReorderListener<SpeechRecording>(adapter, datasource, listView));
                ((DragNDropListView) listView).setRemoveListener(new RemoveListenerImpl<SpeechRecording>(adapter, listView));
                ((DragNDropListView) listView).setDragListener(new DragListenerImpl());
            }
           
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
       
        // Register the ListView  for Context menu  
        registerForContextMenu(getListView());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RequestCodes.RENAME_SPEECH_RECORDING_REQUEST_CODE && resultCode == RESULT_OK) {
            final long newSpeechRecordingId = data.getLongExtra("id", DefaultValues.DEFAULT_LONG_VALUE);
            final String newSpeechRecordingTitle = data.getStringExtra("text");
            final SpeechRecording speechRecording = new SpeechRecording(newSpeechRecordingId, newSpeechRecordingTitle, speechID);
            final int position = data.getIntExtra("position", DefaultValues.DEFAULT_INT_VALUE);
                 
            // Get the speech item to update.
            final SpeechRecording speechRecordingToUpdate = 
                    adapter.getItem(position);
            
            // Update the title.
            speechRecordingToUpdate.setTitle(speechRecording.getTitle());
            adapter.notifyDataSetChanged();
            
            // Save the changes to the database.
            new RenameSpeechRecordingTask(datasource, speechRecordingToUpdate).execute();
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        v.showContextMenu();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.speech_recording_option_menu, menu);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
       
        final SpeechRecording speechRecording = (SpeechRecording) getListAdapter().getItem(info.position);
        
        switch (item.getItemId()) {
            case R.id.play_speech_recording:
                playSpeechRecording(speechRecording);
                return true;
                         
            case R.id.rename_speech_recording:
                renameSpeechRecording(speechRecording, info.position);
                return true;
                
            case R.id.share_speech_recording:
                emailSpeech(speechRecording);
                Log.d(TAG, "Speech Recording shared");
                return true;
       
            case R.id.delete_speech_recording:
                new DeleteTask<SpeechRecording>(datasource, speechRecording).execute();
                adapter.remove(speechRecording);
                adapter.notifyDataSetChanged();
                return true;
                
            case R.id.action_settings:
                Log.d(TAG, "options selected");
                return true;
        }
     
        return false;
    }
      
    private void playSpeechRecording(SpeechRecording speechRecording) {
        final AudioCntl audioCntl = AudioCntl.INSTANCE;
        audioCntl.startPlaying(speechRecording.getFile());
    }
    
    /**
     * Opens an activity to edit the speech recording title.
     * @param speechRecording the speech recording to rename
     */
    private void renameSpeechRecording(SpeechRecording speechRecording, int position) {
        final Intent intent = new Intent(this, EditTextActivity.class);
        intent.putExtra("position", position );
        intent.putExtra("id", speechRecording.getId());
        intent.putExtra("text", speechRecording.getTitle());
        startActivityForResult(intent, RequestCodes.RENAME_SPEECH_RECORDING_REQUEST_CODE);
    }
    
    private void emailSpeech(SpeechRecording speechRecording) {
        final String file = Misc.FILE_DIRECTORY + speechRecording.getFile() + Misc.AUDIO_EXTENSION;
        Log.d(TAG, "File: " + file);
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        // Set the type to 'email'.
        shareIntent.setType("vnd.android.cursor.dir/email");
        // The attachment.
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file));
        // The mail subject.
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, file);
        startActivity(Intent.createChooser(shareIntent , ""));
    }

}