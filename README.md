## My Little ProPra

I created "My Little ProPra" in late 2012 as a part of a Java/Android project I did for my Computer Science study.

It is an online multiplayer game and chat app targeting Android 2.3.3, were a user can select an avatar (a pony - guess why it's called "My Little ProPra") and then explore a virtual world (all tiles will be received from the server). When near to other users, chat messages can be send to them and reponses will be rendered as speech bubbles above the avatars.
All communication to the server is done via socket communication and a proprietary binary chunk protocol. 

Some of the implementation problems I was faced with:

- Handling concurrency / multithreading for a non-blocking user experience
- Checking, parsing/decoding and creating binary data
- Caching resources effectively
- Drawing bitmaps and refreshing views
- Using only 24MB of memory: `java.lang.OutOfMemoryError: bitmap size exceeds VM budget`

I'm afraid, the server is no longer online and the implementation is unknown, so this code isn't very useful any more. As there were many parts containing proprietary images, there are no screenshots available. In addition, I've stripped away all the view definitions and custom strings, so only the `src` folder is published here to avoid any license violations. **So please, don't expect this to be working, it simply does not work any more!**
