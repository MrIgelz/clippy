gitlabAccesses = null;
	/*gitlabAccesses = [
		{ uuid: 1, host: 'https://gitlab.firma.com', expiresInDays: 145 },
		{ uuid: 2, host: 'https://gitlab.com', expiresInDays: 12 },
		{ uuid: 3, host: 'https://git.alte-firma.de', expiresInDays: -1 }
	];*/
	newGitlabHost: string = '';
	newGitlabPat: string = '';

	getExpirationSeverity(days: number): 'success' | 'warn' | 'danger' {
		if (days <= 0) return 'danger';
		if (days <= 14) return 'warn';
		return 'success';
	}

	deleteAccess(access: GitlabAccess) {
		// Öffnet PrimeNG p-confirmDialog
	}

	displayInfoDialog = false;
	selectedAccess: any = null; // Dein lokales DB-Objekt
	displayAddDialog = false;

	isLoadingLiveDetails = false;
	liveFetchError = false;
	liveDetails: GitlabLiveDetails | null = null;

	isSaving = false;
	saveErrorType: 'UNAUTHORIZED' | 'NETWORK' | 'TIMEOUT' | 'UNKNOWN' | null = null;

	// Unsere simplen Daten-Variablen
	newName = '';
	newUrl = 'https://gitlab.com';
	newToken = '';

	openAddDialog() {
		// Variablen zurücksetzen beim Öffnen
		this.newName = '';
		this.newUrl = 'https://gitlab.com';
		this.newToken = '';
		this.saveErrorType = null;

		this.displayAddDialog = true;
	}

	addGitlabAccess(): void {
		this.adminService.addGitlabAccess(this.newGitlabHost, this.newGitlabPat).then((gitlabAccessList: GitlabAccess[]) => {
			this.company.project.gitlabAccessList = gitlabAccessList;
		});
	}

	deleteGitlabAccess(uuid: string): void {
		this.adminService.removeGitlabAccess(uuid).then((gitlabAccessList: GitlabAccess[]) => {
			this.company.project.gitlabAccessList = gitlabAccessList;
		});
	}

	onSaveConnection(urlInput: NgModel, tokenInput: NgModel) {
		urlInput.control.markAsTouched();
		tokenInput.control.markAsTouched();

		if (urlInput.invalid || tokenInput.invalid) return;
	}

	openInfoDialog(access: any) {
		this.selectedAccess = access;
		this.displayInfoDialog = true;
		this.fetchLiveDetails();
	}

	fetchLiveDetails() {
		this.isLoadingLiveDetails = true;
		this.liveFetchError = true;
		this.liveDetails = null;

		// Wir simulieren einen API-Aufruf (1 Sekunde Ladezeit)
		setTimeout(() => {

			// Hier sind unsere Mock-Daten, die alle deine Felder abdecken!
			this.liveDetails = {
				userName: 'project_8472_bot',
				avatarUrl: null, // Testen wir den Fallback
				tokenType: 'PROJECT',
				role: 'Maintainer', // Bot-User haben oft eine Rolle im Projekt

				tokenName: 'Admin Dashboard API Access',
				description: 'Wird genutzt, um Issues über das Dashboard zu verwalten.',
				createdAt: '2026-05-12T08:30:00Z',
				expiresAt: '2027-05-12T08:30:00Z', // Setze das auf null für "Unbegrenzt"
				lastUsedAt: '2026-10-01T14:15:00Z',
				scopes: ['api', 'read_repository', 'write_issue']
			};

			this.isLoadingLiveDetails = false;
		}, 1000);
	}





	savedConnections = [
		{ id: 1, host: 'https://gitlab.com' },
		{ id: 2, host: 'https://gitlab.internal.com' }
	];

	selectedConnection: any = null;
	projectId: string = '';
	webhookSecret: string = '';
