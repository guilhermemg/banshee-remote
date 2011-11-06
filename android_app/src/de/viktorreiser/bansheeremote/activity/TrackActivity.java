package de.viktorreiser.bansheeremote.activity;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import de.viktorreiser.bansheeremote.R;
import de.viktorreiser.bansheeremote.data.BansheeConnection.Command;
import de.viktorreiser.bansheeremote.data.BansheeConnection.OnBansheeCommandHandle;
import de.viktorreiser.bansheeremote.data.BansheeDatabase;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.AlbumI;
import de.viktorreiser.bansheeremote.data.BansheeDatabase.TrackI;
import de.viktorreiser.bansheeremote.data.CoverCache;

/**
 * Browse tracks from synchronized database.
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class TrackActivity extends Activity implements OnBansheeCommandHandle, OnItemClickListener {
	
	// PRIVATE ====================================================================================
	
	private OnBansheeCommandHandle mOldCommandHandler;
	private TrackI [] mTrackEntries;
	private Object [] mAdapterSections;
	private ListView mList;
	private long mAlbumId;
	private long mArtistId;
	
	// PUBLIC =====================================================================================
	
	public static final String EXTRA_ALBUM_ID = "alid";
	public static final String EXTRA_ARTIST_ID = "arid";
	
	// OVERRIDDEN =================================================================================
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		if (CurrentSongActivity.getConnection() == null) {
			finish();
			return;
		}
		
		Object [] data = (Object []) getLastNonConfigurationInstance();
		
		if (data != null) {
			mTrackEntries = (TrackI []) data[0];
			mAdapterSections = (Object []) data[1];
			mAlbumId = (Long) data[2];
			mArtistId = (Long) data[3];
		} else {
			if (getIntent().hasExtra(EXTRA_ALBUM_ID) && getIntent().hasExtra(EXTRA_ARTIST_ID)) {
				mAlbumId = getIntent().getLongExtra(EXTRA_ALBUM_ID, -1);
				mArtistId = getIntent().getLongExtra(EXTRA_ARTIST_ID, -1);
				mTrackEntries = BansheeDatabase.getOrderedTrackIForAlbum(mAlbumId);
			} else if (getIntent().hasExtra(EXTRA_ARTIST_ID)) {
				mArtistId = getIntent().getLongExtra(EXTRA_ARTIST_ID, -1);
				mTrackEntries = BansheeDatabase.getOrderedTrackIForArtist(mArtistId);
			} else {
				mTrackEntries = BansheeDatabase.getOrderedTrackI();
			}
			
			if (mAlbumId < 1) {
				List<SectionEntry> sections = new LinkedList<SectionEntry>();
				Set<String> characters = new TreeSet<String>();
				
				for (int i = 0; i < mTrackEntries.length; i++) {
					String c = mTrackEntries[i].getTitle().substring(0, 1).toUpperCase();
					
					if (!characters.contains(c)) {
						SectionEntry s = new SectionEntry();
						s.character = c;
						s.position = i;
						sections.add(s);
						characters.add(c);
					}
				}
				
				mAdapterSections = sections.toArray();
			}
		}
		
		mOldCommandHandler = CurrentSongActivity.getConnection().getHandleCallback();
		CurrentSongActivity.getConnection().updateHandleCallback(new OnBansheeCommandHandle() {
			@Override
			public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
				mOldCommandHandler.onBansheeCommandHandled(command, params, result);
				
				if (command != null) {
					TrackActivity.this.onBansheeCommandHandled(command, params, result);
				}
			}
		});
		
		setContentView(R.layout.track);
		
		mList = (ListView) findViewById(R.id.list);
		mList.setAdapter(new TrackAdapter());
		mList.setOnItemClickListener(this);
		
		View headerCommon = findViewById(R.id.header_common);
		View headerArtist = findViewById(R.id.header_artist);
		View headerAlbum = findViewById(R.id.header_album);
		
		headerCommon.setVisibility(mAlbumId < 1 && mArtistId < 1 ? View.VISIBLE : View.GONE);
		headerArtist.setVisibility(mArtistId > 0 && mAlbumId < 1 ? View.VISIBLE : View.GONE);
		headerAlbum.setVisibility(mAlbumId > 0 && mArtistId > 0 ? View.VISIBLE : View.GONE);
		
		if (mAlbumId > 0 && mArtistId > 0) {
			String artistName = BansheeDatabase.getArtistI(mArtistId).getName();
			AlbumI album = BansheeDatabase.getAlbumI(mAlbumId);
			
			((TextView) headerAlbum.findViewById(R.id.artist_name)).setText(artistName);
			((TextView) headerAlbum.findViewById(R.id.album_title)).setText(
					album.getTitle() + " (" + album.getTrackCount() + ")");
			
			if (CoverCache.coverExists(album.getArtId())) {
				((ImageView) headerAlbum.findViewById(R.id.cover1)).setImageBitmap(
						CoverCache.getThumbnailedCover(album.getArtId()));
			}
		} else if (mArtistId > 0) {
			((TextView) headerArtist.findViewById(R.id.artist_name)).setText(
					BansheeDatabase.getArtistI(mArtistId).getName()
							+ " (" + mTrackEntries.length + ")");
			
			if (mTrackEntries.length > 25) {
				mList.setFastScrollEnabled(true);
			}
		} else {
			((TextView) headerCommon.findViewById(R.id.track_title)).setText(
					getString(R.string.all_tracks) + " (" + mTrackEntries.length + ")");
			mList.setFastScrollEnabled(true);
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (CurrentSongActivity.getConnection() != null) {
			CurrentSongActivity.getConnection().updateHandleCallback(mOldCommandHandler);
		}
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object [] {mTrackEntries, mAdapterSections, mAlbumId, mArtistId};
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		return CurrentSongActivity.handleKeyEvent(e) ? true : super.dispatchKeyEvent(e);
	}
	
	@Override
	public void onItemClick(AdapterView<?> a, View v, int p, long id) {
		CurrentSongActivity.getConnection().sendCommand(Command.PLAYLIST,
				Command.Playlist.encodePlayTrack(mTrackEntries[p].getId()));
	}
	
	@Override
	public void onBansheeCommandHandled(Command command, byte [] params, byte [] result) {
		switch (command) {
		case COVER:
			String artId = Command.Cover.getId(params);
			Bitmap cover = CoverCache.getThumbnailedCover(artId);
			
			if (cover != null) {
				int childCount = mList.getChildCount();
				
				for (int i = 0; i < childCount; i++) {
					ViewHolder holder = (ViewHolder) mList.getChildAt(i).getTag();
					
					if (artId.equals(holder.cover.getTag())) {
						holder.cover.setImageBitmap(cover);
					}
				}
			}
			break;
		}
	}
	
	// PRIVATE ====================================================================================
	
	private static class ViewHolder {
		public TextView track;
		public TextView artist;
		public ImageView cover;
	}
	
	private static class SectionEntry {
		public String character;
		public int position;
		
		@Override
		public String toString() {
			return character;
		}
	}
	
	private class TrackAdapter extends BaseAdapter implements SectionIndexer {
		
		@Override
		public int getItemViewType(int position) {
			return mAlbumId > 0 && mArtistId > 0 ? 1 : 0;
		}
		
		@Override
		public int getViewTypeCount() {
			return 2;
		}
		
		@Override
		public int getCount() {
			return mTrackEntries.length;
		}
		
		@Override
		public Object getItem(int position) {
			return null;
		}
		
		@Override
		public long getItemId(int position) {
			return 0;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			int type = getItemViewType(position);
			
			if (convertView == null) {
				ViewHolder holder = new ViewHolder();
				
				if (type == 0) {
					convertView = getLayoutInflater().inflate(R.layout.track_item, null);
					holder.artist = (TextView) convertView.findViewById(R.id.artist_name);
					holder.cover = (ImageView) convertView.findViewById(R.id.cover1);
				} else {
					convertView = getLayoutInflater().inflate(R.layout.track_item_simple, null);
				}
				
				holder.track = (TextView) convertView.findViewById(R.id.track_title);
				convertView.setTag(holder);
			}
			
			ViewHolder holder = (ViewHolder) convertView.getTag();
			TrackI i = mTrackEntries[position];
			holder.track.setText(i.getTitle());
			
			if (type == 0) {
				if (mArtistId > 0) {
					holder.artist.setText(i.getAlbum().getTitle());
				} else {
					holder.artist.setText(i.getArtist().getName());
				}
				
				String artId = i.getAlbum().getArtId();
				
				if (CoverCache.coverExists(artId)) {
					holder.cover.setImageBitmap(CoverCache.getThumbnailedCover(artId));
					holder.cover.setTag(null);
				} else {
					CurrentSongActivity.getConnection().sendCommand(
							Command.COVER, Command.Cover.encode(artId), false);
					holder.cover.setImageResource(R.drawable.no_cover);
					holder.cover.setTag(artId);
				}
			}
			
			return convertView;
		}
		
		@Override
		public int getPositionForSection(int section) {
			return ((SectionEntry) mAdapterSections[section]).position;
		}
		
		@Override
		public int getSectionForPosition(int position) {
			return position;
		}
		
		@Override
		public Object [] getSections() {
			return mAdapterSections;
		}
	}
}
