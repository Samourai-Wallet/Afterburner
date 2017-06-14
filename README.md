# Afterburner

### Build:

Import as Android Studio project. Should build "as is". PGP signed tagged releases correspond to builds that were issued via Google Play.

### Child-Pays-For-Parent (CPFP):

Afterburner will restore the BIP39 mnemonic for any BIP44 wallet and allow you to unblock unconfirmed transactions via CPFP.

* the unconfirmed tx must have at least 1 change output
* the parent tx of the unconfirmed transaction should be confirmed
* the change output and, if needed, the other available utxo must be large enough to cover the additional miners' fee + our service charge.

### License:

[Unlicense] (https://github.com/Samourai-Wallet/Afterburner/blob/master/LICENSE)

### Web:

[Afterburner](http://www.afterburnerapp.com/)

### Contributing:

All development goes in 'develop' branch - do not submit pull requests to 'master'.

### Dev contact:

[PGP](http://pgp.mit.edu/pks/lookup?op=get&search=0x72B5BACDFEDF39D7)

### What we do:

[Samourai HQ](http://samouraiwallet.com)

[Paymentcode.io](http://paymentcode.io)

[Segwit activation tracker](http://segw.it)
