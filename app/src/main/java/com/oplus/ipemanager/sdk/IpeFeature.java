package com.oplus.ipemanager.sdk;

import android.os.Parcel;
import android.os.Parcelable;

/** Cleaned from IpeFeature.kt */
public enum IpeFeature implements Parcelable {
    DEMO_MODE,
    FUNCTION_VIBRATION,
    FEEDBACK_VIBRATION;

    public static final Creator<IpeFeature> CREATOR = new Creator<IpeFeature>() {
        @Override public IpeFeature createFromParcel(Parcel in) {
            return IpeFeature.values()[in.readInt()];
        }
        @Override public IpeFeature[] newArray(int size) {
            return new IpeFeature[size];
        }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
}
