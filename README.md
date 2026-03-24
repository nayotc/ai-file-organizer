# ai-file-organizer
AI File Organizer

A tool for automatically classifying and organizing files in the Downloads folder using AI-based content analysis.

Overview

This project addresses the problem of managing an unstructured Downloads folder by automatically organizing files into predefined categories.

Unlike traditional file organizers that rely on filenames or extensions, this system analyzes the actual content of files, including PDFs, to determine their appropriate destination.

The system integrates with the Gemini API for intelligent classification.

Features
AI-based file classification
Content analysis for PDF files
Automatic file sorting into structured directories
Confidence-based decision handling (manual review when needed)
Undo support for reverting file movements
Handling of incomplete downloads (e.g., .crdownload)
System Flow
Monitor the Downloads directory
Detect new files
Extract content (for supported formats such as PDF)
Send data to the AI model for classification
Move the file to the appropriate directory based on the result
If classification confidence is low → mark for manual review
Technologies
Java (Maven)
Gemini API (Google Generative AI)
File system monitoring
PDF processing
SQLite (for tracking operations and undo functionality)
Setup
Clone the repository
git clone https://github.com/nayotc/ai-file-organizer.git
cd ai-file-organizer
Configure API key
export GEMINI_API_KEY=your_api_key_here

(Windows PowerShell)

setx GEMINI_API_KEY "your_api_key_here"
Run the project
mvn clean install
mvn exec:java
