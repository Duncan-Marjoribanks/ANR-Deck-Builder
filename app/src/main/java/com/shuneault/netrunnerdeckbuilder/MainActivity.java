package com.shuneault.netrunnerdeckbuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.json.JSONArray;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.shuneault.netrunnerdeckbuilder.adapters.ExpandableDeckListAdapter;
import com.shuneault.netrunnerdeckbuilder.db.DatabaseHelper;
import com.shuneault.netrunnerdeckbuilder.fragments.DeckFragment;
import com.shuneault.netrunnerdeckbuilder.fragments.MainActivityFragment;
import com.shuneault.netrunnerdeckbuilder.game.Card;
import com.shuneault.netrunnerdeckbuilder.game.CardList;
import com.shuneault.netrunnerdeckbuilder.game.Deck;
import com.shuneault.netrunnerdeckbuilder.helper.AppManager;
import com.shuneault.netrunnerdeckbuilder.helper.CardDownloader;
import com.shuneault.netrunnerdeckbuilder.helper.CardDownloader.CardDownloaderListener;
import com.shuneault.netrunnerdeckbuilder.helper.CardImagesDownloader;
import com.shuneault.netrunnerdeckbuilder.helper.CardImagesDownloader.CardImagesDownloaderListener;
import com.shuneault.netrunnerdeckbuilder.helper.Sorter;
import com.shuneault.netrunnerdeckbuilder.interfaces.OnDeckChangedListener;

public class MainActivity extends ActionBarActivity implements OnDeckChangedListener  {

	// Request Codes for activity launch
	public static final int REQUEST_NEW_IDENTITY = 1;
	public static final int REQUEST_CHANGE_IDENTITY = 2;
	public static final int REQUEST_SETTINGS = 3;
	
	// EXTRAS
	public static final String EXTRA_DECK_ID = "com.shuneault.netrunnerdeckbuilder.EXTRA_DECK_ID";
	
	// Database
	private DatabaseHelper mDb;
	
	private ArrayList<Deck> mDecks;
	private DrawerLayout mDrawerLayout;
	private ExpandableListView mDrawerList;
	private ActionBarDrawerToggle mDrawerToggle;
	private ExpandableDeckListAdapter mDrawerAdapter;
	private ArrayList<String> mDrawerListHeaders;
	private HashMap<String, ArrayList<Deck>> mDrawerListDecks;
	
	// Load the deck on resume
	private Deck mDeck;
	private DeckFragment fragDeck;
	private MainActivityFragment fragMain;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
				
		// GUI
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ExpandableListView) findViewById(R.id.left_drawer);
		
		// Display the main fragment
		if (savedInstanceState == null) {
			fragMain = new MainActivityFragment();
			getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.main_content_frame, fragMain)
				.commit();
		} else {
			// Load back the fragDeck
			fragDeck = (DeckFragment) getSupportFragmentManager().findFragmentByTag("DeckFragment");
		}
		
		// Init some variables
		mDrawerListHeaders = new ArrayList<String>();
		mDrawerListDecks = new HashMap<String, ArrayList<Deck>>();
		mDecks = AppManager.getInstance().getAllDecks();
		mDb = new DatabaseHelper(this);
		AppManager.getInstance().initSharedPrefs(this);
		
		// Load the cards
		if (AppManager.getInstance().getAllCards().size() == 0) {
			File f = new File(getFilesDir(), AppManager.FILE_CARDS_JSON);
            // Use the local provided copy of the card since NetrunnerDB.com got shut down
            if (!f.exists()) {
                InputStream in = getResources().openRawResource(R.raw.cards);
                try {
                    copy(in, f);
                } catch (Exception e) { }
            }
            doLoadCards();
			
		}
		
		// Sort the list
		Collections.sort(mDecks, new Sorter.DeckSorter());
		
		// Load the list
		mDrawerListHeaders.add(Card.Side.SIDE_CORPORATION);
		mDrawerListHeaders.add(Card.Side.SIDE_RUNNER);
		ArrayList<Deck> arrDecksCorp = new ArrayList<Deck>();
		ArrayList<Deck> arrDecksRunner = new ArrayList<Deck>();
		for (Deck deck : mDecks) {
			if (deck.getSide().equals(Card.Side.SIDE_CORPORATION))
				arrDecksCorp.add(deck);
			else
				arrDecksRunner.add(deck);
		}
		mDrawerListDecks.put(mDrawerListHeaders.get(0), arrDecksCorp);
		mDrawerListDecks.put(mDrawerListHeaders.get(1), arrDecksRunner);
		mDrawerAdapter = new ExpandableDeckListAdapter(this, mDrawerListHeaders, mDrawerListDecks);
		// Add the home item
		View vNewCorp = this.getLayoutInflater().inflate(R.layout.list_view_item, null);
		View vNewRunner = this.getLayoutInflater().inflate(R.layout.list_view_item, null);
		((TextView) vNewCorp.findViewById(R.id.lblLabel)).setText(R.string.drawer_new_deck_corp);
		((TextView) vNewRunner.findViewById(R.id.lblLabel)).setText(R.string.drawer_new_deck_runner);
		mDrawerList.addHeaderView(vNewCorp);
		mDrawerList.addHeaderView(vNewRunner);
		mDrawerList.setAdapter(mDrawerAdapter);
		
		// List Click listener
		mDrawerList.setOnChildClickListener(new OnChildClickListener() {
			
			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				
				// Load the deck
				mDeck = mDrawerListDecks.get(mDrawerListHeaders.get(groupPosition)).get(childPosition);
				loadDeckFragment(mDeck);
				
				// Dismiss the drawer
				mDrawerLayout.closeDrawers();
				
				return false;
			}
		});
		mDrawerList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				
				Intent intent = new Intent(MainActivity.this, ChooseIdentityActivity.class);
				switch (arg2) {
				case 0: // New Corp Deck
					intent.putExtra(ChooseIdentityActivity.EXTRA_SIDE_CODE, Card.Side.SIDE_CORPORATION);
					break;
				case 1: // New Runner Deck
					intent.putExtra(ChooseIdentityActivity.EXTRA_SIDE_CODE, Card.Side.SIDE_RUNNER);
					break;
				}
				startActivityForResult(intent, REQUEST_NEW_IDENTITY);
				
				// Dismiss the drawer
				mDrawerLayout.closeDrawers();
			}
			
		});
		
		// Drawer Toggle
		mDrawerToggle = new ActionBarDrawerToggle(
				this,
				mDrawerLayout,
				R.drawable.ic_drawer,
				R.string.drawer_open,
				R.string.drawer_close
				) {
			
			@Override
			public void onDrawerClosed(View drawerView) {
				// 
				super.onDrawerClosed(drawerView);
			}
			
			@Override
			public void onDrawerOpened(View drawerView) {
				// 
				super.onDrawerOpened(drawerView);
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		
		// Load a deck immediately
		if (getIntent().getLongExtra(EXTRA_DECK_ID, 0) > 0) {
			loadDeckFragment(AppManager.getInstance().getDeck(getIntent().getLongExtra(EXTRA_DECK_ID, 0)));
		}
		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK) return;

		switch (requestCode) {
		case REQUEST_NEW_IDENTITY:
			// Get the choosen identity
			Card card = AppManager.getInstance().getAllCards().getCard(data.getStringExtra(ChooseIdentityActivity.EXTRA_IDENTITY_CODE));
			
			// Create a new deck
			mDeck = new Deck("* " + card.getTitle(), card);
			AppManager.getInstance().getAllDecks().add(mDeck);
			
			// Save the new deck in the database
			mDb.createDeck(mDeck);
			
			// Start the new deck activity
			mDrawerListDecks.get(card.getSideCode()).add(0, mDeck);
			loadDeckFragment(mDeck);
			break;
			
		case REQUEST_CHANGE_IDENTITY:
			Card newIdentity = AppManager.getInstance().getCard(data.getStringExtra(ChooseIdentityActivity.EXTRA_IDENTITY_CODE));
			// Forward the info to the DeckFragment
			if (fragDeck != null)
				fragDeck.onDeckIdentityChanged(newIdentity);
			break;
			
		case REQUEST_SETTINGS:
			if (fragDeck != null)
				fragDeck.onSettingsChanged();
			break;
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		// Sync the toggle state after onRestoreInstanceState has occured
		mDrawerToggle.syncState();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		// Close the database connection
		mDb.close();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item))
			return true;
		
		switch (item.getItemId()) {
			case R.id.mnuRefreshCards:
				doDownloadCards();
				break;
			case R.id.mnuOptions:
				startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS);
				break;
			case R.id.mnuAbout:
				PackageInfo pInfo;
				TextView txt = new TextView(this);
				try {
					pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
					txt.setText(getString(R.string.about_text, pInfo.versionName));
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
				txt.setMovementMethod(LinkMovementMethod.getInstance());
				txt.setPadding(25, 25, 25, 25);
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.menu_about);
				builder.setView(txt);
				builder.setPositiveButton(R.string.ok, null);
				builder.show();
				break;
			}
	
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}
	@Override
	public void onDeckNameChanged(Deck deck, String name) {
		mDrawerAdapter.notifyDataSetChanged();
	}

	@Override
	public void onDeckCardsChanged() {
		if (fragDeck != null)
			fragDeck.onDeckCardsChanged();
	}

	@Override
	public void onDeckDeleted(Deck deck) {
		getSupportFragmentManager().beginTransaction().remove(fragDeck).commit();
		getSupportActionBar().removeAllTabs();
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		mDrawerListDecks.get(deck.getSide()).remove(deck);
		mDrawerAdapter.notifyDataSetChanged();	
		// Save
		mDb.deleteDeck(deck);
	}

	@Override
	public void onDeckCloned(Deck deck) {
		mDrawerListDecks.get(deck.getSide()).add(deck);
		mDrawerAdapter.notifyDataSetChanged();
		// Load the deck on screen
		loadDeckFragment(deck);
	}

	@Override
	public void onSettingsChanged() {
	}

	@Override
	public void onDeckIdentityChanged(Card newIdentity) {
		if (fragDeck != null)
			fragDeck.onDeckIdentityChanged(newIdentity);
		
	}

	public void loadDeckFragment(Deck deck, int selectedTab) {
		// Display the deck fragment
		Bundle bundle = new Bundle();
		bundle.putLong(DeckFragment.ARGUMENT_DECK_ID, deck.getRowId());
		bundle.putInt(DeckFragment.ARGUMENT_SELECTED_TAB, selectedTab);
		fragDeck = new DeckFragment();
		fragDeck.setArguments(bundle);
        if (getSupportFragmentManager().findFragmentByTag("DeckFragment") == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_content_frame, fragDeck, "DeckFragment")
                .addToBackStack(null)
                .commit();
        } else {
            getSupportFragmentManager()
                    .popBackStack();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_content_frame, fragDeck, "DeckFragment")
                    .addToBackStack(null)
                    .commit();
        }
		
		// Add the deck to the drawer if necessary
		if (!mDrawerListDecks.get(deck.getSide()).contains(deck))
			mDrawerListDecks.get(deck.getSide()).add(deck);
	}

	public void loadDeckFragment(Deck deck) {
		loadDeckFragment(deck, 0);
	}
	
	public void doLoadCards() {		
		// Cards downloaded, load them
		try {
			/* Load the card list
			 * 
			 * - Create the card
			 * - Add the card to the array
			 * - Generate the faction list
			 * - Generate the side list
			 * - Generate the card set list
			 * 
			 */
			JSONArray jsonFile = AppManager.getInstance().getJSONCardsFile(this);
			CardList arrCards = AppManager.getInstance().getAllCards();
			arrCards.clear();
			for (int i = 0; i < jsonFile.length(); i++) {
				// Create the card and add to the array
				//		Do not load cards from the Alternates set
				Card card = new Card(jsonFile.getJSONObject(i));
				if (!card.getSetName().equals(Card.SetName.ALTERNATES))
					arrCards.add(card);
			}
			
			// Load the decks
			doLoadDecks();
			
		} catch (FileNotFoundException e) {
			doDownloadCards();
			return;
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private void doLoadDecks() {
		// Load the decks from the DB
		mDecks.clear();
		mDecks.addAll(mDb.getAllDecks(true));
	}
	
	private void doDownloadCards() {
		CardDownloader dl = new CardDownloader(this, new CardDownloaderListener() {

			ProgressDialog mDialog;
			
			@Override
			public void onTaskCompleted() {
				// Load the cards in the app
				doLoadCards();
				
				// Close the dialog
				mDialog.dismiss();
				
				// Ask if we want to download the images on if almost no images are downloaded
				if (AppManager.getInstance().getNumberImagesCached(MainActivity.this) < 20) {
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
					builder.setMessage(R.string.download_all_images_question_first_launch);
					builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							// Download all images
							CardImagesDownloader dnl = new CardImagesDownloader(MainActivity.this, new CardImagesDownloaderListener() { 
								
								@Override
								public void onTaskCompleted() {
	
								}
								
								@Override
								public void onImageDownloaded(Card card, int count, int max) {
									if (fragMain != null)
										fragMain.updateInfo();
								}
								
								@Override
								public void onBeforeStartTask(Context context, int max) {
	
								}
							});
							dnl.execute(MainActivity.this);
						}
					});
					builder.setNegativeButton(android.R.string.no, null);
					builder.create().show();
				}
				
				// Update the main info screen
				if (fragMain != null) {
					fragMain.updateInfo();
				}
			}

			@Override
			public void onBeforeStartTask(Context context) {
				// Display a progress dialog
				mDialog = new ProgressDialog(context);
				mDialog.setTitle(getResources().getString(R.string.downloading_cards));
				mDialog.setIndeterminate(true);
				mDialog.setCancelable(false);
				mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				mDialog.setMessage(null);
				mDialog.show();
			}

			@Override
			public void onDownloadError() {
				// Display the error and cancel the ongoing dialog
				mDialog.dismiss();
				
				// If zero cards are available, exit the application
				if (AppManager.getInstance().getAllCards().size() <= 0) {
					Toast.makeText(MainActivity.this, R.string.error_downloading_cards_quit, Toast.LENGTH_LONG).show();
					finish();
				} else {
					Toast.makeText(MainActivity.this, R.string.error_downloading_cards, Toast.LENGTH_LONG).show();
				}
			}
		});
		dl.execute();
	}

    public void copy(InputStream in, File dst) throws IOException {
        //InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

	
}