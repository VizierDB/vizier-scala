# Contributing to Vizier

## General Guidelines

We want everyone to be able to participate here, so when contributing (issues, documentation, code, etc...), please keep the following two guidelines in mind: (i) Think about how your words will be perceived by others before writing them, and (ii) give others the benefit of the doubt when interpreting their words.

## Style 

#### Branches

- The main Vizier branch represents the most recent release candidate.
  - No one pushes directly to the main branch.
- Development branches are labeled `v[major].[minor]` (e.g., v2.1).
  - Pull requests for new features will only be accepted for the most recent development branch.
  - Only pull requests for bugfixes will be accepted for earlier branches.
  - Pull requests must pass the default test case suite: 
    - `mill vizier.test` for backend changes
    - `mill vizier.ui.test` for frontend changes

#### Commits

- A single commit should address a single issue.  Spread distinct changes across multiple commits.
- The first line should be an at-most 100 character summary of the commit, containing:
    - The prefix WIP: if the commit is broken.
    - A brief description of the goal of the commit's changes.
    - An issue reference:
        - (re: #[issue number]) to mark work towards a specific ticket
        - (closes: #[issue number]) to signify that work towards a ticket is done
- A brief list of behaviors of the system that were changed: One line per behavior.  For example:
    - [system component] no longer hangs when passed a [type of input]
    - Added [field] to the schema of [category] to store [summary]
    - UI can now display [state].
- If the reason for the behavioral change is not clear from the summary, add a short discussion providing any necessary context.  For example "[external component] has a bug where if you ask it to [action] then it deletes your hard drive.  Obviously, this would be bad, so we need to sanitize our inputs."

#### Pull Requests

- Ideally, a pull request focuses on one issue, or a linked set of issues.
    - Pull requests for features must be made to the latest development branch.  
    - Only pull requests for bug fixes will be accepted to an earlier branch.
- Pull requests must pass all test cases.
    - ... excepting any test cases that are broken on a development branch.
- The text of the pull request should briefly summarize the key changes made in terms of behaviors of the system that are different (see commit messages above).
    - The text should clearly identify which tickets the pull request is responding to.