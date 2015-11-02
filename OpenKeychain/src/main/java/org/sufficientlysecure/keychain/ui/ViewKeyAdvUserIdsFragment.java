/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.compatibility.DialogFragmentWorkaround;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserPackets;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel;
import org.sufficientlysecure.keychain.ui.adapter.UserIdsAdapter;
import org.sufficientlysecure.keychain.ui.dialog.UserIdInfoDialogFragment;
import org.sufficientlysecure.keychain.util.Log;

public class ViewKeyAdvUserIdsFragment extends LoaderFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    public static final String ARG_DATA_URI = "uri";
    public static final String ARG_HAS_SECRET = "has_secret";

    private ListView mUserIds;

    private static final int LOADER_ID_USER_IDS = 0;

    private UserIdsAdapter mUserIdsAdapter;

    private Uri mDataUri;
    private boolean mHasSecret;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup superContainer, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, superContainer, savedInstanceState);
        View view = inflater.inflate(R.layout.view_key_adv_main_fragment, getContainer());

        mUserIds = (ListView) view.findViewById(R.id.view_key_user_ids);

        mUserIds.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showUserIdInfo(position);
            }
        });

        return root;
    }

    private void showUserIdInfo(final int position) {
        final boolean isRevoked = mUserIdsAdapter.getIsRevoked(position);
        final int isVerified = mUserIdsAdapter.getIsVerified(position);

        DialogFragmentWorkaround.INTERFACE.runnableRunDelayed(new Runnable() {
            public void run() {
                UserIdInfoDialogFragment dialogFragment =
                        UserIdInfoDialogFragment.newInstance(isRevoked, isVerified);

                dialogFragment.show(getActivity().getSupportFragmentManager(), "userIdInfoDialog");
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Uri dataUri = getArguments().getParcelable(ARG_DATA_URI);
        if (dataUri == null) {
            Log.e(Constants.TAG, "Data missing. Should be Uri of key!");
            getActivity().finish();
            return;
        }
        boolean hasSecret = getArguments().getBoolean(ARG_HAS_SECRET);

        loadData(dataUri, hasSecret);
    }

    private void loadData(Uri dataUri, boolean hasSecret) {
        mDataUri = dataUri;
        mHasSecret = hasSecret;

        Log.i(Constants.TAG, "mDataUri: " + mDataUri);

        mUserIdsAdapter = new UserIdsAdapter(getActivity(), null, 0);
        mUserIds.setAdapter(mUserIdsAdapter);

        setHasOptionsMenu(hasSecret);

        // Prepare the loaders. Either re-connect with an existing ones,
        // or start new ones.
        getLoaderManager().initLoader(LOADER_ID_USER_IDS, null, this);
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        setContentShown(false);

        if (id != LOADER_ID_USER_IDS) {
            return null;
        }

        Uri baseUri = UserPackets.buildUserIdsUri(mDataUri);
        return new CursorLoader(getActivity(), baseUri,
                UserIdsAdapter.USER_PACKETS_PROJECTION, null, null, null);
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() != LOADER_ID_USER_IDS) {
            return;
        }

        /* TODO better error handling? May cause problems when a key is deleted,
         * because the notification triggers faster than the activity closes.
         */
        // Avoid NullPointerExceptions...
        if (data.getCount() == 0) {
            return;
        }

        mUserIdsAdapter.swapCursor(data);
        setContentShown(true);
    }

    /**
     * This is called when the last Cursor provided to onLoadFinished() above is about to be closed.
     * We need to make sure we are no longer using it.
     */
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() != LOADER_ID_USER_IDS) {
            return;
        }
        mUserIdsAdapter.swapCursor(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.edit_user_id, menu);
        MenuItem vEditUserIds  = menu.findItem(R.id.menu_edit_user_ids);
        vEditUserIds.setVisible(mHasSecret);
    }

    public void enterEditMode() {
        FragmentActivity activity = getActivity();
        activity.startActionMode(new Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mUserIdsAdapter.setEditMode(new SaveKeyringParcel(0L, new byte[0]));
                getLoaderManager().restartLoader(LOADER_ID_USER_IDS, null, ViewKeyAdvUserIdsFragment.this);

                mode.setTitle("Edit User Ids");
                mode.getMenuInflater().inflate(R.menu.action_edit_uids, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mUserIdsAdapter.setEditMode(null);
                getLoaderManager().restartLoader(LOADER_ID_USER_IDS, null, ViewKeyAdvUserIdsFragment.this);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit_user_ids:
                enterEditMode();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
