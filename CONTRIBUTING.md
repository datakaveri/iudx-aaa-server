
# Contributing

<!-- SPDX-License-Identifier: (MIT OR CC-BY-3.0+) -->

Feedback and contributions are very welcome!

Here's help on how to make contributions, divided into the following sections:

-   general information,
-   [vulnerability reporting](https://github.com/coreinfrastructure/best-practices-badge/blob/main/CONTRIBUTING.md#how_to_report_vulnerabilities),
-   documentation changes,
-   code changes,
-   keeping up the main branch.

## General information

For specific proposals, please provide them as [pull requests](https://github.com/datakaveri/iudx-aaa-server/pulls) or [issues](https://github.com/datakaveri/iudx-aaa-server/issues) via our [GitHub site](https://github.com/datakaveri/iudx-aaa-server).


The [READMEmd](https://github.com/datakaveri/iudx-aaa-server/blob/main/README.md) file explains how to install the program locally (highly recommended if you're going to make code changes). It also provides a quick start guide.


### Pull requests and different branches recommended

Pull requests are preferred, since they are specific. For more about how to create a pull request, see
<https://help.github.com/articles/using-pull-requests/>.

We recommend creating different branches for different (logical) changes, and creating a pull request when you're done into the main branch. See the GitHub documentation on [creating branches](https://help.github.com/articles/creating-and-deleting-branches-within-your-repository/) and [using pull requests](https://help.github.com/articles/using-pull-requests/).

### How we handle proposals

We use GitHub to track proposed changes via its [issue tracker](https://github.com/datakaveri/iudx-aaa-server/issues) and [pull requests](https://github.com/datakaveri/iudx-aaa-server/pulls).
Specific changes are proposed using those mechanisms.

If there are questions or objections, the conversation area of that issue or pull request is used to resolve it.

### Two-person review

Our policy is that at least 50% of all proposed modifications will be reviewed before release by a person other than the author, to determine if it is a worthwhile modification and free of known issues which would argue against its inclusion

We achieve this by splitting proposals into two kinds:

1. Low-risk modifications.  These modifications are being proposed by people authorized to commit directly, pass all tests, and are unlikely to have problems. These include documentation/text updates where no risk (such as a security risk) have been identified.  The project lead can decide that any particular  modification is low-risk.
2. Other modifications.  These other modifications need to be reviewed by someone else or the project lead can decide to accept the modification. Typically this is done by creating a branch and a pull request so that it can be reviewed before accepting it.

### License (MIT)

All (new) contributed material must be released under the [MIT license](./LICENSE).

### We are proactive

In general we try to be proactive to detect and eliminate mistakes and vulnerabilities as soon as possible, and to reduce their impact when they do happen.
We use a defensive design and coding style to reduce the likelihood of mistakes,
a variety of tools that try to detect mistakes early, and an automatic test suite with significant coverage.
We also release the software as open source software so others can review it.

Since early detection and impact reduction can never be perfect, we also try to detect and repair problems during deployment as quickly as possible.

## <span id="how_to_report_vulnerabilities">Vulnerability reporting (security issues)</a>

If you find a significant vulnerability, or evidence of one, please send an email to the security contacts that you have such information, and we'll tell you the next steps.
For now, the security contacts are:
IUDX admin <[admin@iudx.org.in](mailto:support@iudx.org.in)> and 
IUDX Support <[support@iudx.org.in](mailto:support@iudx.org.in)>.

Please use an email system (like Gmail) that supports hop-to-hop encryption using STARTTLS when reporting vulnerabilities. Examples of such systems include Gmail, Outlook.com, and runbox.com. 
See [STARTTLS Everywhere](https://starttls-everywhere.org/) if you wish to learn more about efforts to encourage the use of STARTTLS.
Your email client should use encryption to communicate with your email system (i.e., if you use a web-based email client then use HTTPS, and if you use email client software then configure it to use encryption). Hop-to-hop encryption isn't as strong as end-to-end encryption,
but we've decided that it's strong enough for this purpose and it's much easier to get everyone to use it.

We will gladly give credit to anyone who reports a vulnerability
so that we can fix it. If you want to remain anonymous or pseudonymous instead,
please let us know that; we will gladly respect your wishes.

## Documentation changes

Most of the documentation is in "markdown" format. All markdown files use the .md filename extension.

## Code changes

To make changes to the "aaa-server" ,you may find the following helpful; [README.md](https://github.com/datakaveri/iudx-aaa-server/blob/main/README.md)
installation and implementation information.

The code should strive to be DRY (don't repeat yourself), clear, and obviously correct.
Some technical debt is inevitable, just don't bankrupt us with it. Improved refactorizations are welcome.

### Automated tests

When adding or changing functionality, please include new tests for them as
part of your contribution.

We require the Java code to have at least 90% statement coverage;
please ensure your contributions do not lower the coverage below that minimum.
The Java code uses Junit as the test framework. Additional tests are very welcome.

We encourage tests to be created first, run to ensure they fail, and then add code to implement the test (aka test driven development).


### Other tools

Here are some other tools we sometimes use for checking quality or security.

* OWASP ZAP web application security scanner.
  You are encouraged to use this and other web application scanners to find and
  fix problems.

## Git commit messages

When writing git commit messages, try to follow the guidelines in
[How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/):

1.  Separate subject from body with a blank line
2.  Limit the subject line to 50 characters.
    (We're flexible on this, but *do* limit it to 72 characters or less.)
3.  Capitalize the subject line
4.  Do not end the subject line with a period
5.  Use the imperative mood in the subject line (*command* form)
6.  Wrap the body at 72 characters ("<tt>fmt -w 72</tt>")
7.  Use the body to explain what and why vs. how
    (git tracks how it was changed in detail, don't repeat that)

## Keeping up with the main branch

The installer adds a git remote named 'upstream'.
Running 'git pull upstream main' will pull the current version from
upstream, enabling you to sync with upstream.

You can reset this, if something has happened to it, using:

~~~~sh
git remote add upstream \
  https://github.com/datakaveri/iudx-aaa-server.git
~~~~


## Keeping dependencies up-to-date

We use [Dependabot alerts](https://docs.github.com/en/code-security/dependabot/working-with-dependabot) to keep the dependencies in our project up to date.. 

