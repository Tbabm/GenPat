/**
 * Copyright (C) SEI, PKU, PRC. - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited Proprietary and Confidential.
 * Written by Jiajun Jiang<jiajun.jiang@pku.edu.cn>.
 */

package mfix.core.locator;

import mfix.common.conf.Constant;
import mfix.common.java.D4jSubject;
import mfix.common.util.JavaFile;
import mfix.common.util.LevelLogger;
import mfix.common.util.Pair;
import mfix.common.util.Triple;
import mfix.common.util.Utils;
import mfix.core.node.ast.LineRange;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: Jiajun
 * @date: 2019-03-20
 */
public class SBFLocator extends AbstractFaultLocator {

    public SBFLocator(D4jSubject subject) {
        super(subject);
        locateFault(0);
    }

    @Override
    protected void locateFault(double threshold) {
//        try {
//            ExecuteCommand.execute(CmdFactory.createSbflCmd((D4jSubject) _subject, Constant.SBFL_TIMEOUT),
//                    _subject.getJDKHome(), Constant.D4J_HOME);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public List<String> getFailedTestCases() {
        return _failedTests;
    }

    private String getBuggyLineSuspFile(){
        return Utils.join(Constant.SEP, Constant.LOCATOR_SUSP_FILE_BASE,
                _subject.getName(), String.valueOf(_subject.getId()), "stmt-susps.txt");
    }

    private String getOchiaiFile() {
        return Utils.join(Constant.SEP, Constant.OCHIAI_RESULT,
                _subject.getName(), _subject.getId() + ".txt");
    }

    private String getRealtimeResultFile() {
        return Utils.join(Constant.SEP, Constant.PROJ_REALTIME_LOC_BASE,
                _subject.getName(), _subject.getId() + ".txt");
    }

    @Override
    public List<Location> getLocations(int topK) {
        List<Location> lines ;//= getSortedSuspStmt(getBuggyLineSuspFile(), topK);
//        if (lines == null || lines.isEmpty()) {
            lines = ochiaiResult(getOchiaiFile(), topK);
//        }
        return lines;
    }

    private List<Location> ochiaiResult(String path, int topk) {
        List<String> lines = JavaFile.readFileToStringList(path);
        List<Triple<String, Integer, Double>> suspStmt = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if(line.length() > 0){
                String[] info = line.split(",");
                if(info.length < 2){
                    LevelLogger.error("Location format error : " + line);
                    continue;
                }
                String[] linesInfo = info[0].split("#");
                String stmt = linesInfo[0];
                Integer lineNumber = Integer.parseInt(linesInfo[1]);
                int index = stmt.indexOf("$");
                if(index > 0){
                    stmt = stmt.substring(0, index);
                }
                suspStmt.add(new Triple<>(stmt, lineNumber, 0.0));
            }
        }
        return transformLine2Method(suspStmt, topk);
    }

    private List<Location> getSortedSuspStmt(String fileName, int topK) {
        //org.jfree.chart.renderer.category.LineAndShapeRenderer#201,0.1889822365046136
        List<String> lines = JavaFile.readFileToStringList(fileName);
        if (lines == null || lines.isEmpty()) return null;

        List<Pair<String, Double>> suspStmt = new ArrayList<>(lines.size());
        StringBuffer buffer = new StringBuffer();
        int i = lines.get(0).endsWith("Suspiciousness") ? 1 : 0;
        for (; i < lines.size(); i++) {
            String[] lineAndSusp = lines.get(i).split(",");
            if(lineAndSusp.length != 2){
                LevelLogger.error("Suspicious line format error : " + lines.get(i));
                continue;
            }
            buffer.append(lineAndSusp).append(Constant.NEW_LINE);

            String stmt = lineAndSusp[0];
            double susp = Double.parseDouble(lineAndSusp[1]);
            suspStmt.add(new Pair<>(stmt, susp));
        }

        // write result to file
        JavaFile.writeStringToFile(getRealtimeResultFile(), buffer.toString());

        suspStmt = suspStmt.stream()
                .sorted(Comparator.comparingDouble(Pair<String, Double>::getSecond).reversed())
                .collect(Collectors.toList());


        List<Triple<String, Integer, Double>> buggyLines = new LinkedList<>();
        for(Pair<String, Double> pair : suspStmt){
            String[] clazzAndLine = pair.getFirst().split("#");
            if(clazzAndLine.length != 2){
                LevelLogger.error("Suspicous statement format error : " + pair.getFirst());
                continue;
            }

            String clazz = clazzAndLine[0];
            int index = clazz.indexOf("$");
            if(index > 0){
                clazz = clazz.substring(0, index);
            }
            int lineNum = Integer.parseInt(clazzAndLine[1]);
            buggyLines.add(new Triple<>(clazz, lineNum, pair.getSecond()));
        }
        return transformLine2Method(buggyLines, topK);
    }

    private List<Location> transformLine2Method(List<Triple<String, Integer, Double>> clazzLineSusp, int topK) {

        Set<String> relFiles = clazzLineSusp.stream()
                .map(t -> t.getFirst()).collect(Collectors.toSet());
        String srcBase = _subject.getHome() + _subject.getSsrc();

        Map<String, List<Pair<LineRange, String>>> result = new HashMap<>();
        for (String f : relFiles) {
            String file = Utils.join(Constant.SEP, srcBase, f.replace('.', Constant.SEP) + ".java");
            final List<Pair<LineRange, String>> list = result.getOrDefault(f, new LinkedList<>());
            final CompilationUnit unit = JavaFile.genAST(file);
            if (unit != null) {
                unit.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(MethodDeclaration node) {
                        int start = unit.getLineNumber(node.getStartPosition());
                        int end = unit.getLineNumber(node.getStartPosition() + node.getLength());
                        list.add(new Pair<>(new LineRange(start, end), node.getName().getIdentifier()));
                        return true;
                    }
                });
            }
            result.put(f, list);
        }

        List<Location> locations = new LinkedList<>();
        for (Triple<String, Integer, Double> triple : clazzLineSusp) {
            if (locations.size() >= topK) {
                break;
            }
            String clazz = triple.getFirst();
            List<Pair<LineRange, String>> list = result.get(clazz);
            if (list != null) {
                int line = triple.getSecond();
                Iterator<Pair<LineRange, String>> itor = list.iterator();
                Pair<LineRange, String> pair;
                while(itor.hasNext()) {
                    pair = itor.next();
                    if (pair.getFirst().contains(line)) {
                        locations.add(new Location(clazz, null, pair.getSecond(), line, triple.getThird()));
                        itor.remove();
                        break;
                    }
                }
            }
        }

        return locations;
    }

}