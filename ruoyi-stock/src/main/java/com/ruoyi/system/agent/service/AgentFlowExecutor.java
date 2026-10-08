package com.ruoyi.system.agent.service;

import com.ruoyi.system.agent.domain.AgentAnalysisRequest;
import com.ruoyi.system.agent.domain.AnalysisSession;
import com.ruoyi.system.agent.domain.DebateRound;
import com.ruoyi.system.agent.domain.EvidenceCard;
import com.ruoyi.system.agent.domain.JudgeVerdict;
import com.ruoyi.system.agent.domain.PositionAdvice;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.agent.enums.AnalysisNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点执行器：执行单个分析节点的业务逻辑（页面模式与快速模式共用）。
 */
@Slf4j
@Service
public class AgentFlowExecutor {

    @Autowired
    private AgentDataCollectService collectService;

    @Autowired
    private SubAgentService subAgentService;

    @Autowired
    private DebateJudgeService debateJudgeService;

    /**
     * 执行一个节点（异常向上抛，由调用方决定中断/失败处理）。
     */
    public void executeNode(AnalysisSession session, AnalysisNode node) {
        switch (node) {
            case DATA_FETCH: {
                AgentAnalysisRequest request = session.getRequest();
                StockDataContext ctx = collectService.collect(request);
                collectService.logCollectSummary(ctx);
                session.setDataContext(ctx);
                break;
            }
            case CHAN: {
                addCard(session, subAgentService.chanAgent(session.getDataContext()));
                break;
            }
            case TECH: {
                addCard(session, subAgentService.techAgent(session.getDataContext()));
                break;
            }
            case PATTERN: {
                addCard(session, subAgentService.patternAgent(session.getDataContext()));
                break;
            }
            case FUNDAMENTAL: {
                addCard(session, subAgentService.fundamentalAgent(session.getDataContext()));
                break;
            }
            case DEBATE: {
                List<DebateRound> transcript =
                        debateJudgeService.runDebate(session.getEvidenceCards(), session.getDataContext());
                PositionAdvice position =
                        debateJudgeService.positionSizer(session.getEvidenceCards(), transcript, session.getDataContext());
                session.setDebateTranscript(transcript);
                session.setPositionAdvice(position);
                break;
            }
            case JUDGE: {
                JudgeVerdict verdict = debateJudgeService.judge(
                        session.getEvidenceCards(), session.getDebateTranscript(),
                        session.getPositionAdvice(), session.getDataContext());
                session.setVerdict(verdict);
                break;
            }
            default:
                throw new IllegalStateException("未知节点: " + node);
        }
    }

    private void addCard(AnalysisSession session, EvidenceCard card) {
        if (session.getEvidenceCards() == null) {
            session.setEvidenceCards(new ArrayList<>());
        }
        session.getEvidenceCards().add(card);
        log.info("子Agent完成: {} {} 证据{}条", session.getRequest().getStockCode(),
                card.getAgentName(), card.getEvidence() == null ? 0 : card.getEvidence().size());
    }
}
