# Pilot

Welcome to Fattorestreet.com, a dedicated space for index fund investing. Fattore Street has been in development since 2022 and  serves as my personal laboratory where I explore the intersection of full-stack development, economics, and high-fidelity financial data. Let me share a bit of what I am currently working on.

## The Shift to Data Sovereignty
A major focus over the last few months has been achieving total data independence. I have transitioned away from unreliable, restricted aggregators in favor of the SEC EDGAR system. By pulling directly from these files, the platform now accesses revenue, net income, and balance sheet facts that are verified and free for commercial use.

## IEX: The Exchange for the People
For pricing, I have integrated IEX, an exchange that aligns with the interests of the everyday investor. Their famous 38 mile fiber optic "speed bump" protects against high frequency trading tactics, making them a natural partner for an index focused project. Successfully loading these prices into the database has been a massive technical win for the platform’s performance.

## The Total Return Milestone
Currently, we are working with raw trading prices. The final, "champagne popping" achievement will be the integration of corporate actions, specifically stock splits and dividends. While SEC EDGAR endpoints for these facts are notoriously complex, capturing them is essential for calculating the adjusted prices and "Total Return" plots seen on professional financial tools.

## LLM Powered Engineering
Tools such as Cursor and Claude Code are now being utilized for this project. I have been impressed by the performance of these LLM powered workflows. The mixture of RAG-based systems and model orchestration makes these incredibly dangerous tools for rapid development.