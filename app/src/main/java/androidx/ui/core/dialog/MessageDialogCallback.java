package androidx.ui.core.dialog;

public class MessageDialogCallback implements OnMessageDialogListener{

    @Override
    public void onMessageDialogCancel(MessageDialog dialog) {
        dialog.dismiss();
    }

    @Override
    public void onMessageDialogConfirm(MessageDialog dialog) {

    }

}
