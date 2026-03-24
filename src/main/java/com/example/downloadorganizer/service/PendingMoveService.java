package com.example.downloadorganizer.service;

import com.example.downloadorganizer.model.PendingMove;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PendingMoveService {

    private final Queue<PendingMove> queue = new ConcurrentLinkedQueue<>();
    private final FileActionService fileActionService;
    private final MoveHistoryService moveHistoryService;

    public PendingMoveService(FileActionService fileActionService,
                              MoveHistoryService moveHistoryService) {
        this.fileActionService = fileActionService;
        this.moveHistoryService = moveHistoryService;
    }

    public void addPendingMove(Path sourcePath, Path targetFolder, String reason) {
        PendingMove pendingMove = new PendingMove(sourcePath, targetFolder, reason);
        queue.add(pendingMove);
        System.out.println("Added to pending queue: " + pendingMove);
    }

    public void processPendingMoves() {
        Iterator<PendingMove> iterator = queue.iterator();

        while (iterator.hasNext()) {
            PendingMove pending = iterator.next();

            try {
                if (!java.nio.file.Files.exists(pending.getSourcePath())) {
                    System.out.println("Pending file no longer exists, removing: " + pending.getSourcePath());
                    iterator.remove();
                    continue;
                }

                pending.incrementAttempts();

                Path originalPath = pending.getSourcePath();
                Path movedTo = fileActionService.moveFileToFolder(
                        originalPath,
                        pending.getTargetFolder()
                );

                moveHistoryService.recordMove(
                        originalPath,
                        movedTo,
                        pending.getReason() + " (processed from pending queue)"
                );

                System.out.println("Pending move completed: " + movedTo);
                iterator.remove();

            } catch (Exception e) {
                if (pending.getAttempts() >= 10) {
                    System.out.println("Pending move failed too many times, removing: " + pending);
                    iterator.remove();
                } else {
                    System.out.println("Pending move still failed, will retry later: " + pending.getSourcePath());
                }
            }
        }
    }

    public void printPendingMoves() {
        if (queue.isEmpty()) {
            System.out.println("Pending queue is empty.");
            return;
        }

        System.out.println("Pending moves:");
        queue.forEach(System.out::println);
    }
}