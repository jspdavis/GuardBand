# The `Guardband-MVVM` folder: check before it is removed

**For:** Jehan (Ishi). **Takes:** about 10 minutes. **Changes nothing:** every
command below only reads. Nothing is committed, deleted or pushed.

---

## What the problem is

There is a folder called `Guardband-MVVM` in the root of the GuardBand
project. It is a **second copy of the GuardBand repo sitting inside the real
one**. It most likely appeared while an AI tool was working in the project.

The app does not use it. The build only includes `:app`, and no code or
Gradle file points at the folder. But the real repo has recorded it, and that
causes two problems:

1. **The real repo stores a pointer to it, not its files.** Git saved it as a
   "gitlink": a link to one commit in another repo. On 24 Oct it pointed at
   `f34487b`, an old GuardBand commit. Your commit on 27 Oct (`c2dfa0e`) moved
   it to **`9448b3c`**, a commit that is **not on GitHub**. That commit can
   only exist inside the `Guardband-MVVM` folder on your laptop.
2. **Android Studio treats it as a second project.** Your 24 Oct commit
   (`6370441`) added it to `.idea/gradle.xml` and `.idea/vcs.xml`, so Studio
   tries to sync it as a second Gradle project and a second Git repo.

**Why not just add it to `.gitignore`?** `.gitignore` only affects things Git
is not tracking yet. The pointer is already committed, so ignoring it changes
nothing. It has to be removed from Git (James will do that).

**Why check first?** If you ever edited files or made commits *inside*
`Guardband-MVVM` (for example, the AI tool opened that folder instead of the
real one), that work never reached GitHub. Removing the folder would lose
it. The steps below tell you whether that happened.

---

## The check

Open a terminal **in the GuardBand project root**, the folder that holds
`app`, `gradlew` and `Guardband-MVVM`. Android Studio's Terminal tab, Git
Bash or PowerShell all work. Run each command and copy what it prints.

### Step 1: is it a git repo at all?

```
git -C Guardband-MVVM log --oneline -15 --all
```

- **Error** (e.g. "not a git repository", "does not have any commits", or "cannot
  change to"): the folder is empty or not a repo. **Nothing is stranded.**
  Skip to "Send back".
- **A list of commits:** carry on.

### Step 2: is there uncommitted work?

```
git -C Guardband-MVVM status
git -C Guardband-MVVM stash list
```

- `status` says **"nothing to commit, working tree clean"** and `stash list`
  prints **nothing**: no uncommitted work.
- Otherwise, the files `status` lists as *modified*, *new* or *untracked*, and
  any stash, are **stranded work**. Note them down.

### Step 3: are its commits already on GitHub?

```
git -C Guardband-MVVM remote -v
git -C Guardband-MVVM fetch origin
git -C Guardband-MVVM log --oneline --branches --not --remotes
```

- `remote -v` should show `github.com/jspdavis/GuardBand`. If it shows
  nothing, write that down and skip the `fetch`.
- The last command lists **commits that exist only in this folder**.
  **Nothing printed** means every commit is already on GitHub.
- If it prints commits, they are **stranded**. For each one, run:

  ```
  git -C Guardband-MVVM show --stat <hash>
  ```

  and note which files it changed.

### Step 4: the commit the real repo points at

```
git -C Guardband-MVVM show --stat 9448b3c
```

This is the commit your 27 Oct push pointed the real repo at. Note its
message and the files it changed. If it says "unknown revision", write that
down too.

---

## Reading the result

| You saw | Meaning |
|---|---|
| Step 1 errored | **Nothing stranded.** |
| Step 2 clean, no stashes, and step 3 printed no commits | **Nothing stranded.** |
| Step 2 listed files, or there is a stash | Uncommitted work is stranded in those files. |
| Step 3 printed commits | Those commits exist only on your laptop. |

**If something is stranded, don't delete anything and don't commit inside
`Guardband-MVVM`.** Send the list below to James first. The work will be
copied into the real repo on `mvvm-main` before the folder goes.

---

## Send back

Message James with:

1. **The verdict:** "Nothing stranded", or what is stranded.
2. What **steps 2 and 3** printed (copy and paste the text).
3. What **step 4** printed (the first few lines are enough).

---

## What happens after

James removes the pointer from the real repo in one commit, without touching
anyone's files on disk:

- `git rm --cached Guardband-MVVM` (takes it out of Git, leaves the folder)
- removes the `Guardband-MVVM` entries from `.idea/gradle.xml` and
  `.idea/vcs.xml`
- adds `/Guardband-MVVM/` to `.gitignore`, so it cannot be committed again

After that commit is on `mvvm-main`, **pull it, then delete your own
`Guardband-MVVM` folder** (close Android Studio first, then delete it in File
Explorer). If Studio asks to "link" or "sync" a second Gradle project after
that, say no.

**To stop it happening again:** when an AI tool works on GuardBand, open the
real project folder (the one with `gradlew` in it) and don't let it clone the
repo into a subfolder. Before committing, check `git status` for unexpected
entries, and add files by name rather than `git add -A`/`git add .`.
